package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

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
        // We have two at-bats to load the configuration: loading from cache and fetching
        // The below variables help them keep track of each other's progress
        AtomicBoolean cacheLoadInProgress = new AtomicBoolean(true);
        AtomicReference<String> fetchErrorMessage = new AtomicReference<>(null);
        // We only want to fire the callback off once; so track whether or not we have yet
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        configurationStore.loadFromCache(new CacheLoadCallback() {
            @Override
            public void onCacheLoadSuccess() {
                cacheLoadInProgress.set(false);
                // If cache loaded successfully, fire off success callback if not yet done so by fetching
                if (callback != null && callbackCalled.compareAndSet(false, true)) {
                    Log.d(TAG, "Initialized from cache");
                    callback.onCompleted();
                }
            }

            @Override
            public void onCacheLoadFail() {
                cacheLoadInProgress.set(false);
                Log.d(TAG, "Did not initialize from cache");
                // If cache loading failed, and fetching failed, fire off the failure callback if not yet done so
                // Otherwise, if fetching has not failed yet, defer to it for firing off callbacks
                if (callback != null && fetchErrorMessage.get() != null && callbackCalled.compareAndSet(false, true)) {
                    Log.e(TAG, "Failed to initialize from fetching or by cache");
                    callback.onError("Cache and fetch failed "+fetchErrorMessage.get());
                }
            }
        });

        Log.d(TAG, "Fetching configuration");
        client.get("/api/randomized_assignment/v3/config", new RequestCallback() {
            @Override
            public void onSuccess(String responseBody) {
                try {
                    Log.d(TAG, "Processing fetch response");
                    configurationStore.setFlagsFromJsonString(responseBody);
                    Log.d(TAG, "Configuration fetch successful");
                    configurationStore.asyncWriteToCache(responseBody);
                } catch (JsonSyntaxException | JsonIOException e) {
                    fetchErrorMessage.set(e.getMessage());
                    Log.e(TAG, "Error loading configuration response", e);
                    // If fetching failed, and cache loading failed, fire off the failure callback if not yet done so
                    // Otherwise, if cache has not finished yet, defer to it for firing off callbacks
                    if (callback != null && !cacheLoadInProgress.get() && callbackCalled.compareAndSet(false, true)) {
                        Log.d(TAG, "Failed to initialize from cache or by fetching");
                        callback.onError("Cache and fetch failed "+e.getMessage());
                    }
                    return;
                }

                // If fetching succeeded, fire off success callback if not yet done so from cache loading
                if (callback != null && callbackCalled.compareAndSet(false, true)) {
                    Log.d(TAG, "Initialized from fetch");
                    callback.onCompleted();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                fetchErrorMessage.set(errorMessage);
                Log.e(TAG, "Error fetching configuration: " + errorMessage);
                // If fetching failed, and cache loading failed, fire off the failure callback if not yet done so
                // Otherwise, if cache has not finished yet, defer to it for firing off callbacks
                if (callback != null && !cacheLoadInProgress.get() && callbackCalled.compareAndSet(false, true)) {
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