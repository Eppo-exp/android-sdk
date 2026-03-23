package cloud.eppo.android;

import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.http.EppoConfigurationRequest;
import cloud.eppo.http.EppoConfigurationResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link EppoConfigurationClient} using OkHttp.
 *
 * <p>This client handles HTTP communication with Eppo's configuration servers, supporting
 * conditional requests via If-None-Match headers for efficient caching, and both GET and POST
 * methods.
 */
public class OkHttpEppoClient implements EppoConfigurationClient {
  private static final Logger log = LoggerFactory.getLogger(OkHttpEppoClient.class);
  private static final String ETAG_HEADER = "ETag";
  private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
  private static final String DEFAULT_CONTENT_TYPE = "application/json; charset=utf-8";

  private final OkHttpClient client;

  /** Creates a new OkHttp client with default timeouts (10 seconds for connect and read). */
  public OkHttpEppoClient() {
    this(buildDefaultClient());
  }

  /**
   * Creates a new OkHttp client with a custom OkHttpClient instance.
   *
   * <p>Use this constructor when you need custom timeouts, interceptors, or other OkHttp
   * configurations.
   *
   * @param client the OkHttpClient instance to use
   */
  public OkHttpEppoClient(OkHttpClient client) {
    this.client = client;
  }

  private static OkHttpClient buildDefaultClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();
  }

  @Override
  public CompletableFuture<EppoConfigurationResponse> execute(EppoConfigurationRequest request) {
    CompletableFuture<EppoConfigurationResponse> future = new CompletableFuture<>();
    Request httpRequest = buildRequest(request);

    log.debug("Executing HTTP request to: {}", redactUrl(httpRequest.url()));

    client
        .newCall(httpRequest)
        .enqueue(
            new Callback() {
              @Override
              public void onResponse(@NotNull Call call, @NotNull Response response) {
                log.debug("Received HTTP response: {}", response.code());
                try {
                  EppoConfigurationResponse configResponse = handleResponse(response);
                  log.debug("Successfully handled response");
                  future.complete(configResponse);
                } catch (Exception e) {
                  log.error("Error handling response", e);
                  future.completeExceptionally(e);
                } finally {
                  response.close();
                }
              }

              @Override
              public void onFailure(@NotNull Call call, @NotNull IOException e) {
                log.error("HTTP request failed: {}", e.getMessage(), e);
                future.completeExceptionally(
                    new RuntimeException(
                        "Unable to fetch from URL " + redactUrl(httpRequest.url()), e));
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

    // Handle HTTP method and body
    switch (request.getMethod()) {
      case POST:
        byte[] body = request.getBody();
        String contentType = request.getContentType();
        if (contentType == null) {
          contentType = DEFAULT_CONTENT_TYPE;
        }
        MediaType mediaType = MediaType.parse(contentType);
        requestBuilder.post(RequestBody.create(body != null ? body : new byte[0], mediaType));
        break;
      case GET:
      default:
        requestBuilder.get();
        break;
    }

    return requestBuilder.build();
  }

  private EppoConfigurationResponse handleResponse(Response response) throws IOException {
    int statusCode = response.code();
    String versionId = response.header(ETAG_HEADER);

    log.debug("Handling response - status: {}, etag: {}", statusCode, versionId);

    if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
      log.debug("Configuration not modified (304)");
      return EppoConfigurationResponse.notModified(versionId);
    }

    if (response.isSuccessful()) {
      ResponseBody body = response.body();
      byte[] bodyBytes = body != null ? body.bytes() : new byte[0];
      log.debug("Configuration fetched successfully, {} bytes", bodyBytes.length);
      return EppoConfigurationResponse.success(statusCode, versionId, bodyBytes);
    }

    // Error response
    ResponseBody body = response.body();
    byte[] errorBytes = body != null ? body.bytes() : null;
    log.warn("Configuration fetch failed with status {}", statusCode);
    return EppoConfigurationResponse.error(statusCode, errorBytes);
  }

  private String redactUrl(HttpUrl url) {
    return url.toString().replaceAll("apiKey=[^&]*", "apiKey=<redacted>");
  }
}
