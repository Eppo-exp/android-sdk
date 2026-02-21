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
  private static final String ETAG_HEADER = "ETag";

  private final OkHttpClient client;

  public TestUrlAdapterClient() {
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
  }

  @NonNull @Override
  public CompletableFuture<EppoConfigurationResponse> get(
      @NonNull EppoConfigurationRequest request) {
    CompletableFuture<EppoConfigurationResponse> future = new CompletableFuture<>();

    // Log for debugging
    android.util.Log.d(
        "TestUrlAdapterClient",
        "BaseUrl: " + request.getBaseUrl() + ", ResourcePath: " + request.getResourcePath());

    // Use ONLY baseUrl, ignoring resourcePath (the v4 SDK appends /flag-config/v1/config)
    // The test server serves config directly at the base URL
    HttpUrl.Builder urlBuilder = HttpUrl.parse(request.getBaseUrl()).newBuilder();

    // Add query parameters
    for (Map.Entry<String, String> param : request.getQueryParams().entrySet()) {
      urlBuilder.addQueryParameter(param.getKey(), param.getValue());
    }

    Request httpRequest = new Request.Builder().url(urlBuilder.build()).get().build();

    client
        .newCall(httpRequest)
        .enqueue(
            new Callback() {
              @Override
              public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                  android.util.Log.d(
                      "TestUrlAdapterClient",
                      "Response code: " + response.code() + ", URL: " + call.request().url());
                  EppoConfigurationResponse configResponse = handleResponse(response);
                  future.complete(configResponse);
                } catch (Exception e) {
                  android.util.Log.e("TestUrlAdapterClient", "Error handling response", e);
                  future.completeExceptionally(e);
                } finally {
                  response.close();
                }
              }

              @Override
              public void onFailure(@NonNull Call call, @NonNull IOException e) {
                android.util.Log.e(
                    "TestUrlAdapterClient", "HTTP request failed: " + e.getMessage(), e);
                future.completeExceptionally(
                    new RuntimeException("HTTP request failed: " + e.getMessage(), e));
              }
            });

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
