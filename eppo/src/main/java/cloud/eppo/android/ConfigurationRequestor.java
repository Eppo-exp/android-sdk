package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        // We have two at-bats to load the configuration; track their success
        AtomicBoolean cacheLoadInProgress = new AtomicBoolean(false);
        AtomicReference<String> fetchErrorMessage = new AtomicReference<>(null);
        // We only want to fire the callback off once; track whether or not we have yet
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        configurationStore.loadFromCache(new CacheLoadCallback() {
            @Override
            public void onCacheLoadSuccess() {
                cacheLoadInProgress.set(false);
                if (callback != null && callbackCalled.compareAndSet(false, true)) {
                    Log.d(TAG, "Initialized from cache");
                    callback.onCompleted();
                }
            }

            @Override
            public void onCacheLoadFail() {
                cacheLoadInProgress.set(false);
                if (callback != null && fetchErrorMessage.get() != null && callbackCalled.compareAndSet(false, true)) {
                    Log.e(TAG, "Failed to initialize from fetching or by cache");
                    callback.onError("Cache and fetch failed "+fetchErrorMessage.get());
                }
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
                    fetchErrorMessage.set(e.getMessage());
                    Log.e(TAG, "Error loading configuration response", e);
                    // If cache loading in progress, defer to it's outcome for firing the success or failure callback
                    if (callback != null && !cacheLoadInProgress.get() && callbackCalled.compareAndSet(false, true)) {
                        Log.d(TAG, "Failed to initialize from cache or by fetching");
                        callback.onError("Cache and fetch failed "+e.getMessage());
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
                fetchErrorMessage.set(errorMessage);
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