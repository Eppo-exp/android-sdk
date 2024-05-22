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
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        configurationStore.loadFromCache(new CacheLoadCallback() {
            @Override
            public void onCacheLoadSuccess() {
                if (callback != null && callbackCalled.compareAndSet(false, true)) {
                    Log.d(TAG, "Initialized from cache");
                    callback.onCompleted();
                }
            }

            @Override
            public void onCacheLoadFail() {
                // no-op; fall-back to Fetch
            }
        });

        Log.d(TAG, "Fetching configuration");
        client.get("/api/flag-config/v1/config", new RequestCallback() {
            @Override
            public void onSuccess(Reader response) {
                try {
                    Log.d(TAG, "Processing fetch response");
                    configurationStore.setFlagsFromResponse(response);
                    Log.d(TAG, "Configuration fetch successful");
                } catch (JsonSyntaxException | JsonIOException e) {
                    Log.e(TAG, "Error loading configuration response", e);
                    if (callback != null && callbackCalled.compareAndSet(false, true)) {
                        Log.d(TAG, "Initialization failure due to fetch response");
                        callback.onError("Unable to request configuration");
                    }
                    return;
                }

                if (callback != null && callbackCalled.compareAndSet(false, true)) {
                    Log.d(TAG, "Initialized from fetch");
                    callback.onCompleted();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Error fetching configuration: " + errorMessage);
                if (callback != null && callbackCalled.compareAndSet(false, true)) {
                    Log.d(TAG, "Initialization failure due to fetch error");
                    callback.onError(errorMessage);
                }
            }
        });
    }

    public FlagConfig getConfiguration(String flagKey) {
        return configurationStore.getFlag(flagKey);
    }
}