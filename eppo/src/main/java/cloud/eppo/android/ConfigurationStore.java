package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.util.concurrent.ConcurrentHashMap;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.FlagConfigResponse;
import cloud.eppo.android.dto.deserializers.EppoValueDeserializer;
import cloud.eppo.android.dto.deserializers.FlagConfigResponseDeserializer;

public class ConfigurationStore {

    private static final String TAG = logTag(ConfigurationStore.class);

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(FlagConfigResponse.class, new FlagConfigResponseDeserializer())
            .registerTypeAdapter(EppoValue.class, new EppoValueDeserializer())
            .serializeNulls()
            .create();
    private final ConfigCacheFile cacheFile;

    private ConcurrentHashMap<String, FlagConfig> flags;

    public ConfigurationStore(Application application, String cacheFileNameSuffix) {
        cacheFile = new ConfigCacheFile(application, cacheFileNameSuffix);
    }

    public void loadFromCache(CacheLoadCallback callback) {
        if (flags != null || !cacheFile.exists()) {
            Log.d(TAG, "Not loading from cache ("+(flags == null ? "null flags" : "non-null flags")+")");
            callback.onCacheLoadFail();
            return;
        }

        AsyncTask.execute(() -> {
            Log.d(TAG, "Loading from cache");
            try {
                FlagConfigResponse configResponse;
                synchronized (cacheFile) {
                    BufferedReader reader = cacheFile.getReader();
                    configResponse = gson.fromJson(reader, FlagConfigResponse.class);
                    reader.close();
                }
                if (configResponse == null || configResponse.getFlags() == null) {
                    throw new JsonSyntaxException("Configuration file missing flags");
                }
                if (configResponse.getFlags().isEmpty()) {
                    throw new IllegalStateException("Cached configuration file has empty flags");
                }
                flags = new ConcurrentHashMap<>(configResponse.getFlags());
                Log.d(TAG, "Loaded " + flags.size() + " flags from cached configuration");
                callback.onCacheLoadSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error loading from local cache", e);
                cacheFile.delete();
                callback.onCacheLoadFail();
            }
        });
    }

    public void setFlagsFromResponse(Reader response) {
        FlagConfigResponse config = gson.fromJson(response, FlagConfigResponse.class);
        if (config == null || config.getFlags() == null) {
            Log.w(TAG, "Flags missing in configuration response");
            flags = new ConcurrentHashMap<>();
        } else {
            flags = new ConcurrentHashMap<>(config.getFlags());
            Log.d(TAG, "Loaded " + flags.size() + " flags from configuration response");
        }

        writeConfigToFile(config);
    }

    private void writeConfigToFile(FlagConfigResponse config) {
        AsyncTask.execute(() -> {
            try {
                synchronized (cacheFile) {
                    BufferedWriter writer = cacheFile.getWriter();
                    gson.toJson(config, writer);
                    writer.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to cache config to file", e);
            }
        });
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
