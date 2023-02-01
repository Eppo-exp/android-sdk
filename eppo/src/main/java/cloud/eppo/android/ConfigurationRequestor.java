package cloud.eppo.android;

import android.util.Log;

import com.google.gson.JsonSyntaxException;

import java.io.Reader;

import cloud.eppo.android.dto.FlagConfig;

public class ConfigurationRequestor {
    private static final String TAG = ConfigurationRequestor.class.getCanonicalName();

    private EppoHttpClient client;
    private ConfigurationStore configurationStore;

    public ConfigurationRequestor(ConfigurationStore configurationStore, EppoHttpClient client) {
        this.configurationStore = configurationStore;
        this.client = client;
    }

    public void load(InitializationCallback callback) {
        boolean usedCache = configurationStore.loadFromCache(callback);

        client.get("/api/randomized_assignment/v2/config", new RequestCallback() {
            @Override
            public void onSuccess(Reader response) {
                try {
                    configurationStore.setFlags(response);
                } catch (JsonSyntaxException e) {
                    // JsonSyntaxException is thrown in cases when a SocketException occurs
                    Log.e(TAG, "Error loading configuration response", e);
                    if (callback != null) {
                        callback.onError("Unable to load configuration from network");
                    }
                    return;
                }

                if (callback != null && !usedCache) {
                    callback.onCompleted();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, errorMessage);
                if (callback != null && !usedCache) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    public FlagConfig getConfiguration(String flagKey) {
        return configurationStore.getFlag(flagKey);
    }
}