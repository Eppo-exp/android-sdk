package cloud.eppo.android.framework.storage;

import cloud.eppo.AbstractConfigurationStore;
import cloud.eppo.api.SerializableEppoConfiguration;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Config store that keeps an in-memory configuration and persists it via a {@link ByteStore} and
 * {@link ConfigurationCodec}.
 *
 * <p>Subscriber notification is handled by {@link AbstractConfigurationStore}.
 */
public class CachingConfigurationStore<ConfigurationType extends SerializableEppoConfiguration>
    extends AbstractConfigurationStore<ConfigurationType> {

  private final ConfigurationCodec<ConfigurationType> codec;
  private final ByteStore byteStore;
  private volatile ConfigurationType configuration;

  protected CachingConfigurationStore(
      @NotNull ConfigurationCodec<ConfigurationType> codec, @NotNull ByteStore byteStore) {
    this.configuration = codec.emptyConfiguration();
    this.codec = codec;
    this.byteStore = byteStore;
  }

  /** Returns the current in-memory configuration. */
  @Override
  @NotNull public ConfigurationType getConfiguration() {
    return configuration;
  }

  @Override
  protected CompletableFuture<Void> persist(@NotNull ConfigurationType config) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null");
    }
    byte[] bytes = codec.toBytes(config);
    return byteStore.write(bytes).thenRun(() -> this.configuration = config);
  }

  /**
   * Loads the configuration from storage without updating the in-memory cache.
   *
   * @return a future that completes with the loaded configuration, or null if storage is empty or
   *     missing
   */
  @NotNull public CompletableFuture<ConfigurationType> loadFromStorage() {
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
