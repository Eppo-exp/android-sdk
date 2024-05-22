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
import java.io.IOException;
import java.io.Reader;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.FlagConfigResponse;
import cloud.eppo.android.dto.adapters.DateAdapter;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;
import cloud.eppo.android.dto.adapters.FlagConfigResponseAdapter;

public class ConfigurationStore {

    private static final String TAG = logTag(ConfigurationStore.class);

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(FlagConfigResponse.class, new FlagConfigResponseAdapter())
            .registerTypeAdapter(EppoValue.class, new EppoValueAdapter())
            .registerTypeAdapter(Date.class, new DateAdapter())
            .serializeNulls()
            .create();
    private final ConfigCacheFile cacheFile;

    private final AtomicBoolean loadedFromFetchResponse = new AtomicBoolean(false);
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
                FlagConfigResponse configResponse = readCacheFile();
                if (configResponse == null || configResponse.getFlags() == null) {
                    throw new JsonSyntaxException("Cached configuration file missing flags");
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
                cacheFile.delete();
                callback.onCacheLoadFail();
            }
        });
    }

    protected FlagConfigResponse readCacheFile() throws IOException {
        FlagConfigResponse configResponse;
        synchronized (cacheFile) {
            BufferedReader reader = cacheFile.getReader();
            configResponse = gson.fromJson(reader, FlagConfigResponse.class);
            reader.close();
        }
        return configResponse;
    }

    public void setFlagsFromResponse(Reader response) {
        FlagConfigResponse config = gson.fromJson(response, FlagConfigResponse.class);
        if (config == null || config.getFlags() == null) {
            Log.w(TAG, "Flags missing in configuration response");
            flags = new ConcurrentHashMap<>();
        } else {
            loadedFromFetchResponse.set(true); // Record that flags were set from a response so we don't later clobber them with a slow cache read
            flags = new ConcurrentHashMap<>(config.getFlags());
            Log.d(TAG, "Loaded " + flags.size() + " flags from configuration response");
        }

        writeConfigToFile(config);
    }

    private void writeConfigToFile(FlagConfigResponse config) {
        AsyncTask.execute(() -> {
            Log.d(TAG, "Saving configuration to file");
            try {
                synchronized (cacheFile) {
                    BufferedWriter writer = cacheFile.getWriter();
                    gson.toJson(config, writer);
                    writer.close();
                }
                Log.d(TAG, "Configuration saved");
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
