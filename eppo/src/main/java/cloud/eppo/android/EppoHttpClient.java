package cloud.eppo.android;

import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EppoHttpClient {
    private final OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final String baseUrl;
    private final String apiKey;

    public EppoHttpClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public void get(String path, RequestCallback callback) {
        HttpUrl httpUrl = HttpUrl.parse(baseUrl + path)
                .newBuilder()
                .addQueryParameter("apiKey", apiKey)
                .build();

        Request request = new Request.Builder().url(httpUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().charStream());
                } else {
                    switch (response.code()) {
                        case HttpURLConnection.HTTP_FORBIDDEN:
                            callback.onFailure("Invalid API key");
                            break;
                        default:
                            if (BuildConfig.DEBUG) {
                                Log.e(EppoHttpClient.class.getCanonicalName(), "Fetch failed with status code: " + response.code());
                            }
                            callback.onFailure("Unable to fetch from URL");
                    }
                }
                response.close();
            }

            @Override
            public void onFailure(Call call, IOException e) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
                callback.onFailure("Unable to fetch from URL");
            }
        });
    }
}

interface RequestCallback {
    void onSuccess(Reader response);
    void onFailure(String errorMessage);
}
