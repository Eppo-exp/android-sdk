package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.android.util.Utils;
import cloud.eppo.api.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public class ConfigurationStore implements AndroidConfigurationStore {

  private static final String TAG = logTag(ConfigurationStore.class);
  private final ConfigCacheFile cacheFile;
  private final Object cacheLock = new Object();

  // default to an empty config
  private volatile Configuration configuration = Configuration.emptyConfig();
  private CompletableFuture<Configuration> cacheLoadFuture = null;

  public ConfigurationStore(Application application, String cacheFileNameSuffix) {
    cacheFile = new ConfigCacheFile(application, cacheFileNameSuffix);
  }

  @NonNull @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  public CompletableFuture<Configuration> loadConfigFromCache() {
    if (cacheLoadFuture != null) {
      return cacheLoadFuture;
    }
    if (!cacheFile.exists()) {
      Log.d(TAG, "Not loading from cache (file does not exist)");

      return CompletableFuture.completedFuture(null);
    }
    return cacheLoadFuture =
        CompletableFuture.supplyAsync(
            () -> {
              Log.d(TAG, "Loading from cache");
              return readCacheFile();
            });
  }

  @Nullable protected Configuration readCacheFile() {
    synchronized (cacheLock) {
      try (InputStream inputStream = cacheFile.getInputStream()) {
        Log.d(TAG, "Attempting to inflate config");
        Configuration config = new Configuration.Builder(Utils.toByteArray(inputStream)).build();
        Log.d(TAG, "Cache load complete");
        return config;
      } catch (IOException e) {
        Log.e("Error loading from the cache: {}", e.getMessage());
        return Configuration.emptyConfig();
      }
    }
  }

  @Override
  public CompletableFuture<Void> saveConfiguration(@NonNull Configuration configuration) {
    return CompletableFuture.supplyAsync(
        () -> {
          synchronized (cacheLock) {
            Log.d(TAG, "Saving configuration to cache file");
            // We do not save bandits yet as they are not supported on mobile.
            try (OutputStream outputStream = cacheFile.getOutputStream()) {
              outputStream.write(configuration.serializeFlagConfigToBytes());
              Log.d(TAG, "Updated cache file");
              this.configuration = configuration;
            } catch (IOException e) {
              Log.e(TAG, "Unable write to cache config to file", e);
              throw new RuntimeException(e);
            }
            return null;
          }
        });
  }
}
