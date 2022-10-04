package com.geteppo.android;

import android.util.Log;

import com.geteppo.android.dto.ConfigurationResponse;

import org.json.JSONObject;

public class ConfigurationRequestor {
    private static final String TAG = ConfigurationRequestor.class.getCanonicalName();

    private final EppoHttpClient client;
    private final ConfigurationStore configurationStore;

    public ConfigurationRequestor(ConfigurationStore configurationStore, EppoHttpClient client) {
        this.configurationStore = configurationStore;
        this.client = client;
    }

    public void load() {
        client.get("/randomized_assignment/v2/config", new RequestCallback() {
            @Override
            public void onSuccess(String response) {
                // TODO
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, errorMessage);
            }
        });
    }

}