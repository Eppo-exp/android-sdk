package cloud.eppo.android;

import static cloud.eppo.android.util.AndroidUtils.logTag;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.IConfigurationStore;
import cloud.eppo.android.util.AndroidUtils;
import cloud.eppo.api.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ConfigurationStore implements IConfigurationStore {

  private static final String TAG = logTag(ConfigurationStore.class);
  private final ConfigCacheFile cacheFile;
  private final Object cacheLock = new Object();
  private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  // default to an empty config
  private volatile Configuration configuration = Configuration.emptyConfig();

  public ConfigurationStore(Application application, String cacheFileNameSuffix) {
    cacheFile = new ConfigCacheFile(application, cacheFileNameSuffix);
  }

  @NonNull @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  @Nullable public Configuration loadConfigFromCache() {
    if (!cacheFile.exists()) {
      Log.d(TAG, "Not loading from cache (file does not exist)");
      return null;
    }
    Log.d(TAG, "Loading from cache");

    return readCacheFile();
  }

  public void loadConfigFromCacheAsync(Configuration.Callback callback) {
    if (!cacheFile.exists()) {
      Log.d(TAG, "Not loading from cache (file does not exist)");
      callback.accept(null);
      return;
    }
    Log.d(TAG, "Loading from cache");

    // Use anonymous inner class instead of lambda for Android API 21 compatibility
    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            Configuration config = readCacheFile();

            // Use anonymous inner class instead of lambda for Android API 21 compatibility
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    callback.accept(config);
                  }
                });
          }
        });
  }

  @Nullable protected Configuration readCacheFile() {
    synchronized (cacheLock) {
      try (InputStream inputStream = cacheFile.getInputStream()) {
        Log.d(TAG, "Attempting to inflate config");
        Configuration config =
            new Configuration.Builder(AndroidUtils.toByteArray(inputStream)).build();
        Log.d(TAG, "Cache load complete");
        return config;
      } catch (IOException e) {
        Log.e(TAG, "Error loading from the cache", e);
        return null;
      }
    }
  }

  @Override
  public void saveConfiguration(@NonNull Configuration configuration) {
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
    }
  }
}
