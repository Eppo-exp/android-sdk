package cloud.eppo.android.framework.storage;

import android.app.Application;
import cloud.eppo.api.Configuration;
import org.jetbrains.annotations.NotNull;

public class FileBackedConfigStore extends CachingConfigurationStore {

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
      @NotNull ConfigurationCodec<Configuration> codec) {
    super(codec, createByteStore(application, cacheFileSuffix, codec));
  }

  private static ByteStore createByteStore(
      Application application, String cacheFileSuffix, ConfigurationCodec<Configuration> codec) {
    ConfigCacheFile cacheFile =
        new ConfigCacheFile(application, cacheFileSuffix, codec.getContentType());
    return new FileBackedByteStore(cacheFile);
  }
}
