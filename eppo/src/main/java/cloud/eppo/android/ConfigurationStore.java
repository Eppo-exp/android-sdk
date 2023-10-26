package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.RandomizationConfigResponse;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;
import cloud.eppo.android.util.Utils;

public class ConfigurationStore {

    private static final String TAG = logTag(ConfigurationStore.class);

    private final Gson gson = new GsonBuilder()
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

    public boolean loadFromCache(InitializationCallback callback) {
        if (flags != null || !cacheFile.exists()) {
            Log.d(TAG, "Not loading from cache ("+(flags == null ? "null flags" : "non-null flags")+")");
            return false;
        }

        AsyncTask.execute(() -> {
            Log.d(TAG, "Loading from cache");
            try {
                synchronized (cacheFile) {
                    InputStreamReader reader = cacheFile.getInputReader();
                    RandomizationConfigResponse configResponse = gson.fromJson(reader, RandomizationConfigResponse.class);
                    reader.close();
                    flags = configResponse.getFlags();
                }
                Log.d(TAG, "Cache loaded successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error loading from local cache", e);
                cacheFile.delete();

                if (callback != null) {
                    callback.onError("Unable to load config from cache");
                }
            }

            if (callback != null) {
                callback.onCompleted();
            }
        });
        return true;
    }

    public void setFlags(Reader response) {
        RandomizationConfigResponse config = gson.fromJson(response, RandomizationConfigResponse.class);
        flags = config.getFlags();

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
