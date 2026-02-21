package cloud.eppo.android;

import androidx.annotation.NonNull;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.http.EppoConfigurationRequest;
import cloud.eppo.http.EppoConfigurationResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Test-only HTTP client that adapts v4 SDK URLs to work with the existing test server.
 *
 * <p>The v4 SDK constructs URLs as: {baseUrl}{resourcePath}?{queryParams} For example:
 * https://test-server/b/main/flag-config/v1/config?apiKey=xxx
 *
 * <p>The existing test server expects: https://test-server/b/main?apiKey=xxx
 *
 * <p>This adapter ignores the resourcePath and fetches directly from baseUrl.
 */
public class TestUrlAdapterClient implements EppoConfigurationClient {
  private static final String TAG = "EppoSDK.TestUrlAdapter";
  private static final String ETAG_HEADER = "ETag";

  private final OkHttpClient client;

  public TestUrlAdapterClient() {
    android.util.Log.i(TAG, "TestUrlAdapterClient CONSTRUCTOR CALLED - instance created");
    android.util.Log.i(
        TAG, "Stack trace: " + android.util.Log.getStackTraceString(new Exception()));
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    android.util.Log.i(TAG, "TestUrlAdapterClient constructor completed");
  }

  @NonNull @Override
  public CompletableFuture<EppoConfigurationResponse> execute(
      @NonNull EppoConfigurationRequest request) {
    android.util.Log.i(TAG, "====== EXECUTE() METHOD CALLED ======");
    CompletableFuture<EppoConfigurationResponse> future = new CompletableFuture<>();

    try {
      // Log for debugging
      android.util.Log.i(
          TAG,
          "execute() called - BaseUrl: "
              + request.getBaseUrl()
              + ", ResourcePath: "
              + request.getResourcePath());
      android.util.Log.i(TAG, "execute() QueryParams: " + request.getQueryParams());
      android.util.Log.i(TAG, "execute() Method: " + request.getMethod());

      // Use ONLY baseUrl, ignoring resourcePath (the v4 SDK appends /flag-config/v1/config)
      // The test server serves config directly at the base URL
      HttpUrl parsedUrl = HttpUrl.parse(request.getBaseUrl());
      if (parsedUrl == null) {
        String error = "Failed to parse baseUrl: " + request.getBaseUrl();
        android.util.Log.e(TAG, error);
        future.completeExceptionally(new RuntimeException(error));
        return future;
      }

      HttpUrl.Builder urlBuilder = parsedUrl.newBuilder();

      // Add query parameters
      for (Map.Entry<String, String> param : request.getQueryParams().entrySet()) {
        urlBuilder.addQueryParameter(param.getKey(), param.getValue());
      }

      HttpUrl finalUrl = urlBuilder.build();
      android.util.Log.i(TAG, "Making request to: " + finalUrl);

      Request httpRequest = new Request.Builder().url(finalUrl).get().build();

      client
          .newCall(httpRequest)
          .enqueue(
              new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                  try {
                    android.util.Log.d(
                        TAG,
                        "Response code: " + response.code() + ", URL: " + call.request().url());
                    EppoConfigurationResponse configResponse = handleResponse(response);
                    future.complete(configResponse);
                  } catch (Exception e) {
                    android.util.Log.e(TAG, "Error handling response", e);
                    future.completeExceptionally(e);
                  } finally {
                    response.close();
                  }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                  android.util.Log.e(TAG, "HTTP request failed: " + e.getMessage(), e);
                  future.completeExceptionally(
                      new RuntimeException("HTTP request failed: " + e.getMessage(), e));
                }
              });
    } catch (Exception e) {
      android.util.Log.e(TAG, "Exception in execute(): " + e.getMessage(), e);
      future.completeExceptionally(e);
    }

    return future;
  }

  private EppoConfigurationResponse handleResponse(Response response) throws IOException {
    int statusCode = response.code();
    String etag = response.header(ETAG_HEADER);

    if (statusCode == 304) {
      return EppoConfigurationResponse.notModified(etag);
    }

    if (!response.isSuccessful()) {
      ResponseBody body = response.body();
      String errorBody = body != null ? body.string() : "(no body)";
      throw new IOException("HTTP error " + statusCode + ": " + errorBody);
    }

    ResponseBody body = response.body();
    if (body == null) {
      throw new IOException("Response body is null");
    }

    byte[] responseBytes = body.bytes();
    return EppoConfigurationResponse.success(statusCode, etag, responseBytes);
  }
}
