package cloud.eppo.android.framework.storage;

import cloud.eppo.IConfigurationStore;
import cloud.eppo.api.SerializableEppoConfiguration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract config store that keeps an in-memory configuration and can persist it via a {@link
 * ByteStore} and {@link ConfigurationCodec}.
 */
public class CachingConfigurationStore<ConfigurationType extends SerializableEppoConfiguration>
    implements IConfigurationStore<ConfigurationType> {

  private final ConfigurationCodec<ConfigurationType> codec;
  private final ByteStore byteStore;
  private volatile ConfigurationType configuration;
  private final List<Consumer<ConfigurationType>> subscribers = new CopyOnWriteArrayList<>();

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

  /**
   * Saves the configuration to storage and updates the in-memory cache.
   *
   * @param config the configuration to save (must not be null)
   * @return a future that completes when the write finishes
   * @throws IllegalArgumentException if config is null
   */
  @Override
  @NotNull public CompletableFuture<Void> saveConfiguration(@NotNull ConfigurationType config) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null");
    }
    byte[] bytes = codec.toBytes(config);
    return byteStore
        .write(bytes)
        .thenRun(
            () -> {
              this.configuration = config;
              for (Consumer<ConfigurationType> subscriber : subscribers) {
                subscriber.accept(config);
              }
            });
  }

  @Override
  public Runnable subscribe(Consumer<ConfigurationType> callback) {
    subscribers.add(callback);
    return () -> subscribers.remove(callback);
  }

  @Override
  public boolean unsubscribe(Consumer<ConfigurationType> callback) {
    return subscribers.remove(callback);
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
