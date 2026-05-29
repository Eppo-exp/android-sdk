package cloud.eppo.android.framework.storage;

import cloud.eppo.IConfigurationStore;
import cloud.eppo.api.Configuration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract config store that keeps an in-memory configuration and can persist it via a {@link
 * ByteStore} and {@link ConfigurationCodec}.
 */
public class CachingConfigurationStore implements IConfigurationStore {

  // Sentinel used by seedCache() to detect that no real configuration has been set yet.
  // Captured once so that seedCache()'s compareAndSet uses reference equality against the same
  // instance stored in the AtomicReference at construction time.
  private static final Configuration EMPTY_SENTINEL = Configuration.emptyConfig();

  private final ConfigurationCodec<Configuration> codec;
  private final ByteStore byteStore;
  private final AtomicReference<Configuration> configuration =
      new AtomicReference<>(EMPTY_SENTINEL);

  protected CachingConfigurationStore(
      @NotNull ConfigurationCodec<Configuration> codec, @NotNull ByteStore byteStore) {
    if (codec == null) {
      throw new IllegalArgumentException("codec must not be null");
    }
    if (byteStore == null) {
      throw new IllegalArgumentException("byteStore must not be null");
    }
    this.codec = codec;
    this.byteStore = byteStore;
  }

  /** Returns the current in-memory configuration. */
  @Override
  @NotNull public Configuration getConfiguration() {
    return configuration.get();
  }

  /**
   * Saves the configuration to storage and updates the in-memory cache.
   *
   * <p>The disk write is performed first. The in-memory cache is updated only after the write
   * succeeds, ensuring that the in-memory state never reflects a configuration that failed to
   * persist. Concurrent saves are safe: the last write to complete successfully wins in memory.
   *
   * @param config the configuration to save (must not be null)
   * @return a future that completes when the write finishes, or completes exceptionally if the
   *     underlying {@link ByteStore} write fails (e.g. with an {@link java.io.IOException})
   * @throws IllegalArgumentException if config is null
   */
  @Override
  @NotNull public CompletableFuture<Void> saveConfiguration(@NotNull Configuration config) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null");
    }
    byte[] bytes = codec.toBytes(config);
    return byteStore
        .write(bytes)
        .whenComplete(
            (v, ex) -> {
              if (ex == null) {
                // Only update in-memory cache after a successful disk write.
                // Use set() rather than compareAndSet() so the most-recently-persisted
                // configuration always wins, regardless of submission order.
                configuration.set(config);
              }
            });
  }

  /**
   * Seeds the in-memory cache with {@code config} without writing to disk.
   *
   * <p>Uses {@code compareAndSet} so that a concurrent {@link #saveConfiguration} call always wins.
   * If the in-memory value has already been updated by a save, this is a no-op.
   *
   * @param config the configuration to seed (must not be null)
   */
  void seedCache(@NotNull Configuration config) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null");
    }
    configuration.compareAndSet(EMPTY_SENTINEL, config);
  }

  /**
   * Loads the configuration from storage without updating the in-memory cache.
   *
   * @return a future that completes with the loaded configuration, or null if storage is empty or
   *     missing
   */
  @NotNull public CompletableFuture<Configuration> loadFromStorage() {
    return byteStore
        .read()
        .thenApply(
            bytes -> {
              if (bytes == null || bytes.length == 0) {
                return null;
              }
              return codec.fromBytes(bytes);
            });
  }

  /**
   * Loads the configuration from storage and seeds the in-memory cache if a non-null configuration
   * is found.
   *
   * <p>Combines {@link #loadFromStorage()} and {@link #seedCache(Configuration)} so callers do not
   * need to reach into the storage package to call the package-private {@code seedCache} method
   * directly.
   *
   * @return a future that completes with the loaded configuration, or null if storage is empty or
   *     missing
   */
  @NotNull public CompletableFuture<Configuration> loadAndSeedFromStorage() {
    // thenApplyAsync (common pool) breaks out of the single-thread IO_EXECUTOR so that
    // downstream continuations (e.g. ConfigurationRequestor.setInitialConfiguration, which
    // calls saveConfiguration → byteStore.write on IO_EXECUTOR) do not deadlock by running
    // on the same thread that is blocked waiting for them.
    return loadFromStorage()
        .thenApplyAsync(
            config -> {
              if (config != null) {
                seedCache(config);
              }
              return config;
            });
  }
}
