package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

import cloud.eppo.android.dto.FlagConfig;

public class ConfigurationRequestor {
    private static final String TAG = logTag(ConfigurationRequestor.class);

    private final EppoHttpClient client;
    private final ConfigurationStore configurationStore;

    public ConfigurationRequestor(ConfigurationStore configurationStore, EppoHttpClient client) {
        this.configurationStore = configurationStore;
        this.client = client;
    }

    public void load(InitializationCallback callback) {
        AtomicBoolean cachedUsed = new AtomicBoolean(false);
        configurationStore.loadFromCache(new CacheLoadCallback() {
            @Override
            public void onCacheLoadSuccess() {
                cachedUsed.set(true);
                if (callback != null) {
                    callback.onCompleted();
                }
            }

            @Override
            public void onCacheLoadFail() {
                cachedUsed.set(false);
            }
        });

        client.get("/api/flag-config/v1/config", new RequestCallback() {
            @Override
            public void onSuccess(Reader response) {
                try {
                    configurationStore.setFlagsFromResponse(response);
                    Log.d(TAG, "Configuration fetch successful");
                } catch (JsonSyntaxException | JsonIOException e) {
                    Log.e(TAG, "Error loading configuration response", e);
                    if (callback != null && !cachedUsed.get()) {
                        callback.onError("Unable to request configuration");
                    }
                    return;
                }

                if (callback != null && !cachedUsed.get()) {
                    callback.onCompleted();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Error fetching configuration: " + errorMessage);
                if (callback != null && !cachedUsed.get()) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    public FlagConfig getConfiguration(String flagKey) {
        return configurationStore.getFlag(flagKey);
    }
}