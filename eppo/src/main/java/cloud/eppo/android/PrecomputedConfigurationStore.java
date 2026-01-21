package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.android.dto.PrecomputedBandit;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.dto.PrecomputedFlag;
import cloud.eppo.android.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Storage for precomputed flags/bandits with disk caching. */
public class PrecomputedConfigurationStore {

  private static final String TAG = logTag(PrecomputedConfigurationStore.class);
  private final PrecomputedCacheFile cacheFile;
  private final Object cacheLock = new Object();

  private volatile PrecomputedConfigurationResponse configuration =
      PrecomputedConfigurationResponse.empty();
  private CompletableFuture<PrecomputedConfigurationResponse> cacheLoadFuture = null;

  public PrecomputedConfigurationStore(Application application, String cacheFileNameSuffix) {
    cacheFile = new PrecomputedCacheFile(application, cacheFileNameSuffix);
  }

  /** Returns the current configuration. */
  @NonNull
  public PrecomputedConfigurationResponse getConfiguration() {
    return configuration;
  }

  /** Returns the salt from the current configuration, or null if not set. */
  @Nullable
  public String getSalt() {
    String salt = configuration.getSalt();
    return (salt != null && !salt.isEmpty()) ? salt : null;
  }

  /** Returns the format from the current configuration, or null if not set. */
  @Nullable
  public String getFormat() {
    String format = configuration.getFormat();
    return (format != null && !format.isEmpty()) ? format : null;
  }

  /** Returns a flag by its MD5-hashed key, or null if not found. */
  @Nullable
  public PrecomputedFlag getFlag(String hashedKey) {
    return configuration.getFlags().get(hashedKey);
  }

  /** Returns a bandit by its MD5-hashed key, or null if not found. */
  @Nullable
  public PrecomputedBandit getBandit(String hashedKey) {
    return configuration.getBandits().get(hashedKey);
  }

  /** Returns the flags map. */
  @NonNull
  public Map<String, PrecomputedFlag> getFlags() {
    return configuration.getFlags();
  }

  /** Returns the bandits map. */
  @NonNull
  public Map<String, PrecomputedBandit> getBandits() {
    return configuration.getBandits();
  }

  /** Updates the configuration with a new response. */
  public void setConfiguration(@NonNull PrecomputedConfigurationResponse newConfiguration) {
    this.configuration = newConfiguration;
  }

  /** Loads configuration from the cache file asynchronously. */
  public CompletableFuture<PrecomputedConfigurationResponse> loadConfigFromCache() {
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
              Log.d(TAG, "Loading precomputed config from cache");
              return readCacheFile();
            });
  }

  /** Reads the cache file and returns the configuration. */
  @Nullable
  protected PrecomputedConfigurationResponse readCacheFile() {
    synchronized (cacheLock) {
      try (InputStream inputStream = cacheFile.getInputStream()) {
        Log.d(TAG, "Attempting to inflate precomputed config");
        byte[] bytes = Utils.toByteArray(inputStream);
        PrecomputedConfigurationResponse config = PrecomputedConfigurationResponse.fromBytes(bytes);
        Log.d(TAG, "Precomputed cache load complete");
        return config;
      } catch (IOException e) {
        Log.e(TAG, "Error loading precomputed config from the cache: " + e.getMessage());
        return PrecomputedConfigurationResponse.empty();
      }
    }
  }

  /** Saves the configuration to the cache file asynchronously. */
  public CompletableFuture<Void> saveConfiguration(
      @NonNull PrecomputedConfigurationResponse newConfiguration) {
    return CompletableFuture.supplyAsync(
        () -> {
          synchronized (cacheLock) {
            Log.d(TAG, "Saving precomputed configuration to cache file");
            try (OutputStream outputStream = cacheFile.getOutputStream()) {
              outputStream.write(newConfiguration.toBytes());
              Log.d(TAG, "Updated precomputed cache file");
              this.configuration = newConfiguration;
            } catch (IOException e) {
              Log.e(TAG, "Unable to write precomputed config to file", e);
              throw new RuntimeException(e);
            }
            return null;
          }
        });
  }

  /** Deletes the cache file. */
  public void deleteCache() {
    cacheFile.delete();
  }
}
