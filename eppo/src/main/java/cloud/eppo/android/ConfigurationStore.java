package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.util.Log;
import androidx.annotation.Nullable;
import cloud.eppo.ufc.dto.FlagConfig;
import cloud.eppo.ufc.dto.FlagConfigResponse;
import cloud.eppo.ufc.dto.adapters.EppoModule;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigurationStore {
  private static final String TAG = logTag(ConfigurationStore.class);
  private final ObjectMapper mapper = new ObjectMapper().registerModule(EppoModule.eppoModule());
  private final ConfigCacheFile cacheFile;
  private final Object cacheLock = new Object();
  private final AtomicBoolean loadedFromFetchResponse = new AtomicBoolean(false);
  @Nullable private Map<String, FlagConfig> flags;

  public ConfigurationStore(Application application, String cacheFileNameSuffix) {
    cacheFile = new ConfigCacheFile(application, cacheFileNameSuffix);
  }

  public void loadFromCache(CacheLoadCallback callback) {
    if (flags != null || !cacheFile.exists()) {
      Log.d(
          TAG,
          "Not loading from cache (" + (flags == null ? "null flags" : "non-null flags") + ")");
      callback.onCacheLoadFail();
      return;
    }

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
        () -> {
          Log.d(TAG, "Loading from cache");
          try {
            FlagConfigResponse configResponse = readCacheFile();
            if (configResponse == null || configResponse.getFlags() == null) {
              throw new JsonParseException("Cached configuration file missing flags");
            }
            if (configResponse.getFlags().isEmpty()) {
              throw new IllegalStateException("Cached configuration file has empty flags");
            }
            if (loadedFromFetchResponse.get()) {
              Log.w(TAG, "Configuration already updated via fetch; ignoring cache");
              callback.onCacheLoadFail();
              return;
            }

            flags = new ConcurrentHashMap<>(configResponse.getFlags());
            Log.d(TAG, "Successfully loaded " + flags.size() + " flags from cached configuration");
            callback.onCacheLoadSuccess();
          } catch (Exception e) {
            Log.e(TAG, "Error loading from local cache", e);
            synchronized (cacheFile) {
              cacheFile.delete();
            }
            callback.onCacheLoadFail();
          }
        });
    executor.shutdown();
  }

  @Nullable protected FlagConfigResponse readCacheFile() throws IOException {
    synchronized (cacheLock) {
      try (BufferedReader reader = cacheFile.getReader()) {
        return mapper.readValue(reader, FlagConfigResponse.class);
      }
    }
  }

  public void setFlagsFromJsonString(String jsonString) throws JsonProcessingException {
    FlagConfigResponse config = mapper.readValue(jsonString, FlagConfigResponse.class);
    if (config == null || config.getFlags() == null) {
      Log.w(TAG, "Flags missing in configuration response");
      flags = new ConcurrentHashMap<>();
    } else {
      loadedFromFetchResponse.set(
          true); // Record that flags were set from a response so we don't later clobber them with a
      // slow cache read
      flags = new ConcurrentHashMap<>(config.getFlags());
      Log.d(TAG, "Loaded " + flags.size() + " flags from configuration response");
    }
  }

  public void asyncWriteToCache(String jsonString) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
        () -> {
          Log.d(TAG, "Saving configuration to cache file");
          try {
            synchronized (cacheLock) {
              try (BufferedWriter writer = cacheFile.getWriter()) {
                writer.write(jsonString);
              }
            }
            Log.d(TAG, "Updated cache file");
          } catch (IOException e) {
            Log.e(TAG, "Unable write to cache config to file", e);
          }
        });
    executor.shutdown();
  }

  public FlagConfig getFlag(String flagKey) {
    if (flags == null) {
      Log.w(TAG, "Request for flag " + flagKey + " before flags have been loaded");
      return null;
    } else if (flags.isEmpty()) {
      Log.w(TAG, "Request for flag " + flagKey + " with empty flags");
    }
    return flags.get(flagKey);
  }
}
