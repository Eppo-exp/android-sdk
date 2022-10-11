package cloud.eppo.android;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.RandomizationConfigResponse;

public class ConfigurationRequestor {
    private static final String TAG = ConfigurationRequestor.class.getCanonicalName();

    private EppoHttpClient client;
    private ConfigurationStore configurationStore;
    private Gson gson = new GsonBuilder().registerTypeAdapter(EppoValue.class, new EppoValueAdapter()).create();

    public ConfigurationRequestor(ConfigurationStore configurationStore, EppoHttpClient client) {
        this.configurationStore = configurationStore;
        this.client = client;
    }

    public void load(InitializationCallback callback) {
        client.get("/randomized_assignment/v2/config", new RequestCallback() {
            @Override
            public void onSuccess(Reader response) {
                RandomizationConfigResponse config = gson.fromJson(response, RandomizationConfigResponse.class);
                configurationStore.setFlags(config.getFlags());

                if (callback != null) {
                    callback.onCompleted();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, errorMessage);
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    public FlagConfig getConfiguration(String flagKey) {
        return configurationStore.getFlag(flagKey);
    }
}