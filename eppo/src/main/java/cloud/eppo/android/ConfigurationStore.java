package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.IConfigurationStore;
import cloud.eppo.api.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationStore implements IConfigurationStore {

  private static final String TAG = logTag(ConfigurationStore.class);
  private static final Logger log = LoggerFactory.getLogger(ConfigurationStore.class);
  private final ConfigCacheFile cacheFile;
  private final Object cacheLock = new Object();
  private Configuration configuration;
  private CompletableFuture<Configuration> cacheLoadFuture = null;

  public ConfigurationStore(Application application, String cacheFileNameSuffix) {
    cacheFile = new ConfigCacheFile(application, cacheFileNameSuffix);
  }

  @Override
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
    try (InputStream inputStream = cacheFile.getInputStream()) {
      Log.d(TAG, "Attempting to inflate config");
      Configuration config = Configuration.readFromStream(inputStream);
      Log.d(TAG, "Cache load complete");
      return config;
    } catch (IOException e) {
      log.error("Error loading from the cache: {}", e.getMessage());
      return Configuration.emptyConfig();
    }
  }

  @Override
  public CompletableFuture<Void> saveConfiguration(@NonNull Configuration configuration) {
    CompletableFuture<Void> saveFuture = new CompletableFuture<>();
    ExecutorService executor = Executors.newCachedThreadPool();
    executor.execute(
        () -> {
          synchronized (cacheLock) {
            Log.d(TAG, "Saving configuration to cache file");
            try (OutputStream outputStream = cacheFile.getOutputStream()) {
              configuration.writeToStream(outputStream);
              Log.d(TAG, "Updated cache file");
              this.configuration = configuration;
              saveFuture.complete(null);
            } catch (IOException e) {
              Log.e(TAG, "Unable write to cache config to file", e);
              saveFuture.completeExceptionally(e);
            }
          }
        });
    executor.shutdown();
    return saveFuture;
  }
}
