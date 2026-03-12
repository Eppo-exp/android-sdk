package cloud.eppo.androidexample;

import cloud.eppo.android.OkHttpEppoClient;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.http.EppoConfigurationRequest;
import cloud.eppo.http.EppoConfigurationResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;

/**
 * Custom {@link EppoConfigurationClient} that injects additional HTTP headers into every
 * configuration request.
 *
 * <p>Delegates the actual HTTP work to {@link OkHttpEppoClient} via a custom {@link OkHttpClient}
 * built with an interceptor. Useful for attaching authentication tokens, tracing IDs, or any other
 * app-specific headers without re-implementing the full HTTP logic.
 */
public class HeaderInjectingEppoClient implements EppoConfigurationClient {
  private final OkHttpEppoClient delegate;
  private final Map<String, String> extraHeaders;

  /**
   * Creates a client that adds the given headers to every request.
   *
   * @param extraHeaders headers to inject; keys and values must be valid HTTP header tokens
   */
  public HeaderInjectingEppoClient(Map<String, String> extraHeaders) {
    super();
    this.extraHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(extraHeaders));

    OkHttpClient httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(
                chain -> {
                  Request.Builder builder = chain.request().newBuilder();
                  for (Map.Entry<String, String> header : this.extraHeaders.entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                  }
                  return chain.proceed(builder.build());
                })
            .build();

    this.delegate = new OkHttpEppoClient(httpClient);
  }

  @NotNull @Override
  public CompletableFuture<EppoConfigurationResponse> execute(
      @NotNull EppoConfigurationRequest request) {
    return delegate.execute(request);
  }
}
