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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp implementation of {@link EppoConfigurationClient}.
 *
 * <p>Handles both GET requests (for UFC config) and POST requests (for precomputed assignments).
 */
public class OkHttpConfigurationClient implements EppoConfigurationClient {
  private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
  private static final String ETAG_HEADER = "ETag";

  private final OkHttpClient client;

  /** Creates a new OkHttp client with default timeouts (10 seconds for connect and read). */
  public OkHttpConfigurationClient() {
    this(buildDefaultClient());
  }

  /**
   * Creates a new OkHttp client with a custom OkHttpClient instance.
   *
   * @param client the OkHttpClient instance to use
   */
  public OkHttpConfigurationClient(@NonNull OkHttpClient client) {
    this.client = client;
  }

  private static OkHttpClient buildDefaultClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();
  }

  @NonNull @Override
  public CompletableFuture<EppoConfigurationResponse> execute(
      @NonNull EppoConfigurationRequest request) {
    CompletableFuture<EppoConfigurationResponse> future = new CompletableFuture<>();
    Request httpRequest = buildRequest(request);

    client
        .newCall(httpRequest)
        .enqueue(
            new Callback() {
              @Override
              public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                  EppoConfigurationResponse configResponse = handleResponse(response);
                  future.complete(configResponse);
                } catch (Exception e) {
                  future.completeExceptionally(e);
                } finally {
                  response.close();
                }
              }

              @Override
              public void onFailure(@NonNull Call call, @NonNull IOException e) {
                future.completeExceptionally(
                    new RuntimeException("HTTP request failed: " + e.getMessage(), e));
              }
            });

    return future;
  }

  private Request buildRequest(EppoConfigurationRequest request) {
    HttpUrl.Builder urlBuilder =
        HttpUrl.parse(request.getBaseUrl() + request.getResourcePath()).newBuilder();

    for (Map.Entry<String, String> param : request.getQueryParams().entrySet()) {
      urlBuilder.addQueryParameter(param.getKey(), param.getValue());
    }

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());

    // Add conditional request header if we have a previous version
    String lastVersionId = request.getLastVersionId();
    if (lastVersionId != null && !lastVersionId.isEmpty()) {
      requestBuilder.header(IF_NONE_MATCH_HEADER, lastVersionId);
    }

    // Handle GET or POST based on request method
    if (request.getMethod() == EppoConfigurationRequest.HttpMethod.POST) {
      byte[] body = request.getBody();
      String contentType = request.getContentType();
      MediaType mediaType = contentType != null ? MediaType.parse(contentType) : null;
      RequestBody requestBody = RequestBody.create(body != null ? body : new byte[0], mediaType);
      requestBuilder.post(requestBody);
    } else {
      requestBuilder.get();
    }

    return requestBuilder.build();
  }

  private EppoConfigurationResponse handleResponse(Response response) throws IOException {
    int statusCode = response.code();
    String etag = response.header(ETAG_HEADER);

    // Handle 304 Not Modified
    if (statusCode == 304) {
      return EppoConfigurationResponse.notModified(etag);
    }

    // Handle non-successful responses
    if (!response.isSuccessful()) {
      ResponseBody body = response.body();
      String errorBody = body != null ? body.string() : "(no body)";
      throw new IOException("HTTP error " + statusCode + ": " + errorBody);
    }

    // Handle successful response
    ResponseBody body = response.body();
    if (body == null) {
      throw new IOException("Response body is null");
    }

    byte[] responseBytes = body.bytes();

    return EppoConfigurationResponse.success(statusCode, etag, responseBytes);
  }
}
