package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
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
    private final Set<String> flagConfigsToSaveToPrefs = new HashSet<>();
    private final SharedPreferences sharedPrefs;
    private final ConfigCacheFile cacheFile;

    private ConcurrentHashMap<String, FlagConfig> flags;

    public ConfigurationStore(Application application) {
        cacheFile = new ConfigCacheFile(application);
        this.sharedPrefs = Utils.getSharedPrefs(application);
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
                synchronized (cacheFile) {
                    InputStreamReader reader = cacheFile.getInputReader();
                    RandomizationConfigResponse configResponse = gson.fromJson(reader, RandomizationConfigResponse.class);
                    reader.close();
                    if (configResponse == null || configResponse.getFlags() == null) {
                        throw new JsonSyntaxException("Configuration file missing flags");
                    }
                    flags = configResponse.getFlags();
                    updateConfigsInSharedPrefs();
                }
                Log.d(TAG, "Cache loaded successfully");
                callback.onCacheLoadSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error loading from local cache", e);
                cacheFile.delete();
                callback.onCacheLoadFail();
            }
        });
    }

    public void setFlags(Reader response) {
        RandomizationConfigResponse config = gson.fromJson(response, RandomizationConfigResponse.class);
        if (config == null || config.getFlags() == null) {
            Log.w(TAG, "Flags missing in configuration response");
            flags = new ConcurrentHashMap<>();
        } else {
            flags = config.getFlags();
        }

        // update any existing flags already in shared prefs
        updateConfigsInSharedPrefs();

        if (!flagConfigsToSaveToPrefs.isEmpty()) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            for (String plaintextFlagKey : flagConfigsToSaveToPrefs) {
                FlagConfig flagConfig = getFlag(plaintextFlagKey);
                if (flagConfig == null) {
                    continue;
                }

                String hashedFlagKey = Utils.getMD5Hex(plaintextFlagKey);
                writeFlagToSharedPrefs(hashedFlagKey, flagConfig, editor);
            }
            editor.apply();
            flagConfigsToSaveToPrefs.clear();
        }

        writeConfigToFile(config);
    }

    private void updateConfigsInSharedPrefs() {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        for (String hashedFlagKey : flags.keySet()) {
            if (sharedPrefs.contains(hashedFlagKey)) {
                writeFlagToSharedPrefs(hashedFlagKey, flags.get(hashedFlagKey), editor);
            }
        }
        editor.apply();
    }

    private void writeFlagToSharedPrefs(String hashedFlagKey, FlagConfig config, SharedPreferences.Editor editor) {
        editor.putString(hashedFlagKey, gson.toJson(config));
    }

    private void writeConfigToFile(RandomizationConfigResponse config) {
        AsyncTask.execute(() -> {
            try {
                synchronized (cacheFile) {
                    OutputStreamWriter writer = cacheFile.getOutputWriter();
                    gson.toJson(config, writer);
                    writer.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to cache config to file", e);
            }
        });
    }

    private FlagConfig getFlagFromSharedPrefs(String hashedFlagKey) {
        try {
            return gson.fromJson(sharedPrefs.getString(hashedFlagKey, null), FlagConfig.class);
        } catch (Exception e) {
            Log.e(TAG, "Unable to load flag from prefs", e);
        }
        return null;
    }

    public FlagConfig getFlag(String flagKey) {
        String hashedFlagKey = Utils.getMD5Hex(flagKey);
        if (flags == null) {
            FlagConfig flagFromSharedPrefs = getFlagFromSharedPrefs(hashedFlagKey);
            if (flagFromSharedPrefs != null) {
                return flagFromSharedPrefs;
            }
            flagConfigsToSaveToPrefs.add(flagKey);
            return null;
        }
        return flags.get(hashedFlagKey);
    }
}
