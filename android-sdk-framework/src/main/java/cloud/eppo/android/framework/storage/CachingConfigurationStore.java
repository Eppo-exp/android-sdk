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

  private final ConfigurationCodec<Configuration> codec;
  private final ByteStore byteStore;
  private final AtomicReference<Configuration> configuration =
      new AtomicReference<>(Configuration.emptyConfig());

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
    Configuration previousConfiguration = configuration.get();
    byte[] bytes = codec.toBytes(config);
    configuration.set(config); // optimistic update — in-memory reflects last submitted save
    return byteStore
        .write(bytes)
        .whenComplete(
            (v, ex) -> {
              if (ex != null) {
                // Revert the optimistic update, but only if no later save has superseded this
                // one. compareAndSet atomically checks that the in-memory value is still the
                // one we wrote; if a concurrent save has already advanced it, the revert is
                // skipped.
                //
                // Edge case: if two concurrent saves both fail their IO writes, the second
                // failure's revert may land on a value that was itself never persisted (the
                // first save's value). This is acceptable — the in-memory state may diverge
                // from disk, but the next successful save will reconcile them. Saves are
                // serialized through a single-thread IO_EXECUTOR, so true concurrent IO
                // failures are unlikely in practice.
                configuration.compareAndSet(config, previousConfiguration);
              }
            });
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
}
