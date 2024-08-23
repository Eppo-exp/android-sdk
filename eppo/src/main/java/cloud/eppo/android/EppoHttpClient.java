package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

class EppoHttpClient {
  private static final String TAG = logTag(EppoHttpClient.class);

  private final OkHttpClient client;
  private final String baseUrl;
  private final String apiKey;
  private final String sdkName;

  EppoHttpClient(String baseUrl, String apiKey, String sdkName) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.sdkName = sdkName;
    this.client =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
  }

  public void get(String path, RequestCallback callback) {
    HttpUrl httpUrl =
        HttpUrl.parse(baseUrl + path)
            .newBuilder()
            .addQueryParameter("apiKey", apiKey)
            .addQueryParameter("sdkName", getSdkName())
            .addQueryParameter("sdkVersion", BuildConfig.EPPO_VERSION)
            .build();

    Request request = new Request.Builder().url(httpUrl).build();
    client
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                  Log.d(TAG, "Fetch successful");
                  try {
                    callback.onSuccess(response.body().string());
                  } catch (IOException ex) {
                    callback.onFailure("Failed to read response from URL " + httpUrl);
                  }
                } else {
                  switch (response.code()) {
                    case HttpURLConnection.HTTP_FORBIDDEN:
                      callback.onFailure("Invalid API key");
                      break;
                    default:
                      if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Fetch failed with status code: " + response.code());
                      }
                      callback.onFailure("Bad response from URL " + httpUrl);
                  }
                }
                response.close();
              }

              @Override
              public void onFailure(Call call, IOException e) {
                Log.e(
                    TAG,
                    "Http request failure: "
                        + e.getMessage()
                        + " "
                        + Arrays.toString(e.getStackTrace()),
                    e);
                callback.onFailure("Unable to fetch from URL " + httpUrl);
              }
            });
  }

  /**
   * SDK name pulled out into its own function so it can be overridden by tests to force
   * unobfuscated configurations
   */
  protected String getSdkName() {
    return this.sdkName;
  }
}

interface RequestCallback {
  void onSuccess(String responseBody);

  void onFailure(String errorMessage);
}
