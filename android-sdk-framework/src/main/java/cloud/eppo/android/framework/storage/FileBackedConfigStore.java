package cloud.eppo.android.framework.storage;

import android.app.Application;
import cloud.eppo.api.Configuration;
import org.jetbrains.annotations.NotNull;

public class FileBackedConfigStore extends CachingConfigurationStore implements java.io.Closeable {

  private final FileBackedByteStore byteStore;

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
    this(
        codec,
        new FileBackedByteStore(
            new ConfigCacheFile(application, cacheFileSuffix, codec.getContentType())));
  }

  private FileBackedConfigStore(
      @NotNull ConfigurationCodec<Configuration> codec,
      @NotNull FileBackedByteStore byteStore) {
    super(codec, byteStore);
    this.byteStore = byteStore;
  }

  /**
   * Releases the background IO executor held by the underlying {@link FileBackedByteStore}. Call
   * this when the store is no longer needed (e.g. on reinitialize) to avoid leaking threads.
   */
  @Override
  public void close() throws java.io.IOException {
    byteStore.close();
  }
}
