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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.RandomizationConfigResponse;
import cloud.eppo.android.dto.deserializers.EppoValueAdapter;
import cloud.eppo.android.dto.deserializers.RandomizationConfigResponseDeserializer;
import cloud.eppo.android.util.Utils;

public class ConfigurationStore {

    private static final String TAG = logTag(ConfigurationStore.class);

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(RandomizationConfigResponse.class, new RandomizationConfigResponseDeserializer())
            .registerTypeAdapter(EppoValue.class, new EppoValueAdapter())
            .serializeNulls()
            .create();
    private final ConfigCacheFile cacheFile;

    private AtomicBoolean loadedFromFetchResponse = new AtomicBoolean(false);
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
                RandomizationConfigResponse configResponse = readCacheFile();
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

                flags = configResponse.getFlags();
                Log.d(TAG, "Cache loaded successfully");
                callback.onCacheLoadSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error loading from local cache", e);
                cacheFile.delete();
                callback.onCacheLoadFail();
            }
        });
    }

    protected RandomizationConfigResponse readCacheFile() throws IOException {
        RandomizationConfigResponse configResponse;
        synchronized (cacheFile) {
            BufferedReader reader = cacheFile.getReader();
            configResponse = gson.fromJson(reader, RandomizationConfigResponse.class);
            reader.close();
        }
        return configResponse;
    }

    public void setFlagsFromResponse(Reader response) {
        RandomizationConfigResponse config = gson.fromJson(response, RandomizationConfigResponse.class);
        if (config == null || config.getFlags() == null) {
            Log.w(TAG, "Flags missing in configuration response");
            flags = new ConcurrentHashMap<>();
        } else {
            flags = config.getFlags();
            loadedFromFetchResponse.set(true); // Record that flags were set from a response so we don't later clobber them with a slow cache read
            Log.d(TAG, "Loaded " + flags.size() + " flags from configuration response");
        }

        writeConfigToFile(config);
    }

    private void writeConfigToFile(RandomizationConfigResponse config) {
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
        String hashedFlagKey = Utils.getMD5Hex(flagKey);
        if (flags == null) {
            Log.w(TAG, "Request for flag " + flagKey + " before flags have been loaded");
            return null;
        } else if (flags.isEmpty()) {
            Log.w(TAG, "Request for flag " + flagKey + " with empty flags");
        }
        return flags.get(hashedFlagKey);
    }
}
