package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EppoHttpClient {
    private static final String TAG = logTag(EppoHttpClient.class);

    private final OkHttpClient client;

    private final String baseUrl;
    private final String apiKey;

    public EppoHttpClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.client = buildOkHttpClient();
    }

    private static OkHttpClient buildOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS);

        return builder.build();
    }

    public void get(String path, RequestCallback callback) {
        HttpUrl httpUrl = HttpUrl.parse(baseUrl + path)
                .newBuilder()
                .addQueryParameter("apiKey", apiKey)
                .addQueryParameter("sdkName", getSdkName())
                .addQueryParameter("sdkVersion", BuildConfig.EPPO_VERSION)
                .build();

        Request request = new Request.Builder().url(httpUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Fetch successful");
                    callback.onSuccess(response.body().charStream());
                } else {
                    switch (response.code()) {
                        case HttpURLConnection.HTTP_FORBIDDEN:
                            callback.onFailure("Invalid API key");
                            break;
                        default:
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, "Fetch failed with status code: " + response.code());
                            }
                            callback.onFailure("Bad response from URL "+httpUrl);
                    }
                }
                response.close();
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Http request failure: "+e.getMessage()+" "+Arrays.toString(e.getStackTrace()), e);
                callback.onFailure("Unable to fetch from URL "+httpUrl);
            }
        });
    }

    /**
     * SDK name pulled out into its own function so it can be overridden by tests to force
     * unobfuscated configurations
     */
    protected String getSdkName() {
        return "android";
    }
}

interface RequestCallback {
    void onSuccess(Reader response);
    void onFailure(String errorMessage);
}
