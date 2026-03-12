package cloud.eppo.android.framework.storage;

import cloud.eppo.IConfigurationStore;
import cloud.eppo.api.Configuration;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract config store that keeps an in-memory configuration and can persist it via a {@link
 * ByteStore} and {@link ConfigurationCodec}.
 */
public class CachingConfigurationStore implements IConfigurationStore {

  private final ConfigurationCodec<Configuration> codec;
  private final ByteStore byteStore;
  private volatile Configuration configuration = Configuration.emptyConfig();

  protected CachingConfigurationStore(
      @NotNull ConfigurationCodec<Configuration> codec, @NotNull ByteStore byteStore) {
    this.codec = codec;
    this.byteStore = byteStore;
  }

  /** Returns the current in-memory configuration. */
  @Override
  @NotNull public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * Saves the configuration to storage and updates the in-memory cache.
   *
   * @param config the configuration to save (must not be null)
   * @return a future that completes when the write finishes
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
        .thenRun(
            () -> {
              this.configuration = config;
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
