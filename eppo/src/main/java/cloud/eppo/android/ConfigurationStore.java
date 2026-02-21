package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.IConfigurationStore;
import cloud.eppo.android.util.Utils;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.parser.ConfigurationParseException;
import cloud.eppo.parser.ConfigurationParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public class ConfigurationStore implements IConfigurationStore {

  private static final String TAG = logTag(ConfigurationStore.class);
  private final ConfigCacheFile cacheFile;
  private final ConfigurationParser<?> configurationParser;
  private final Object cacheLock = new Object();

  // default to an empty config
  private volatile Configuration configuration = Configuration.emptyConfig();
  private CompletableFuture<Configuration> cacheLoadFuture = null;

  // Store raw bytes for caching - v4 no longer has serialization on Configuration
  @Nullable private volatile byte[] cachedFlagConfigBytes = null;

  public ConfigurationStore(
      Application application, String cacheFileNameSuffix, ConfigurationParser<?> parser) {
    cacheFile = new ConfigCacheFile(application, cacheFileNameSuffix);
    this.configurationParser = parser;
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
        byte[] bytes = Utils.toByteArray(inputStream);
        FlagConfigResponse flagConfig = configurationParser.parseFlagConfig(bytes);
        Configuration config = new Configuration.Builder(flagConfig).build();
        // Store the raw bytes for potential re-caching
        this.cachedFlagConfigBytes = bytes;
        Log.d(TAG, "Cache load complete");
        return config;
      } catch (IOException e) {
        Log.e(TAG, "Error loading from the cache: " + e.getMessage());
        return Configuration.emptyConfig();
      } catch (ConfigurationParseException e) {
        Log.e(TAG, "Error parsing cached configuration: " + e.getMessage());
        return Configuration.emptyConfig();
      }
    }
  }

  @Override
  public CompletableFuture<Void> saveConfiguration(@NonNull Configuration configuration) {
    return CompletableFuture.supplyAsync(
        () -> {
          synchronized (cacheLock) {
            // Update in-memory configuration
            this.configuration = configuration;

            // Try to save to disk cache using raw bytes if available
            if (cachedFlagConfigBytes != null) {
              Log.d(TAG, "Saving configuration to cache file");
              try (OutputStream outputStream = cacheFile.getOutputStream()) {
                outputStream.write(cachedFlagConfigBytes);
                Log.d(TAG, "Updated cache file");
              } catch (IOException e) {
                Log.e(TAG, "Unable to write cache config to file", e);
                // Don't throw - in-memory config was already updated
              }
            } else {
              Log.d(
                  TAG,
                  "No raw bytes available for caching - disk cache will not be updated. "
                      + "Use saveConfigurationWithBytes() for full caching support.");
            }
            return null;
          }
        });
  }

  /**
   * Saves configuration with raw bytes for disk caching.
   *
   * <p>This method should be called when raw flag config bytes are available. The bytes will be
   * stored for disk caching and parsed to create the in-memory Configuration.
   *
   * @param flagConfigBytes The raw flag configuration JSON bytes from the server
   * @return A future that completes when the configuration is saved
   * @throws ConfigurationParseException if the bytes cannot be parsed
   */
  public CompletableFuture<Void> saveConfigurationWithBytes(@NonNull byte[] flagConfigBytes)
      throws ConfigurationParseException {
    // Parse the bytes first to ensure they're valid
    FlagConfigResponse flagConfig = configurationParser.parseFlagConfig(flagConfigBytes);
    Configuration config = new Configuration.Builder(flagConfig).build();

    // Store the raw bytes for caching
    this.cachedFlagConfigBytes = flagConfigBytes;

    // Save using the standard method
    return saveConfiguration(config);
  }
}
