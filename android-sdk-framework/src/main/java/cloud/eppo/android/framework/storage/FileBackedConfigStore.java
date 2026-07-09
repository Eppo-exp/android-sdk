package cloud.eppo.android.framework.storage;

import android.app.Application;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.SerializableEppoConfiguration;

import org.jetbrains.annotations.NotNull;

public class FileBackedConfigStore<
  ConfigurationType extends SerializableEppoConfiguration
> extends CachingConfigurationStore<ConfigurationType> {

  /**
   * Creates a FileBackedStore with the specified configuration.
   *
   * @param application the Android application context
   * @param cacheFileSuffix suffix for the cache file name (e.g. "v4-flags-abc123")
   * @param codec the codec for serializing/deserializing configurations
   */
  public FileBackedConfigStore(
      @NotNull Application application,
      @NotNull String cacheFileSuffix,
      @NotNull ConfigurationCodec<ConfigurationType> codec) {
    super(codec, createByteStore(application, cacheFileSuffix, codec));
  }

  private static <
        ConfigurationType extends SerializableEppoConfiguration
      > ByteStore createByteStore(
      Application application, String cacheFileSuffix, ConfigurationCodec<ConfigurationType> codec) {
    ConfigCacheFile cacheFile =
        new ConfigCacheFile(application, cacheFileSuffix, codec.getContentType());
    return new FileBackedByteStore(cacheFile);
  }
}
