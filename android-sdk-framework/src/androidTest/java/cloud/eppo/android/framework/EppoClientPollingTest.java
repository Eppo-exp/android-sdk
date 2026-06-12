package cloud.eppo.android.framework;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import cloud.eppo.api.Configuration;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.http.EppoConfigurationRequest;
import cloud.eppo.http.EppoConfigurationResponse;
import cloud.eppo.parser.ConfigurationParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for EppoClient polling pause/resume functionality.
 *
 * <p>These tests focus on verifying that pausePolling() and resumePolling() can be called safely in
 * various sequences, and that polling actually stops and resumes as expected.
 */
public class EppoClientPollingTest {
  private static final String DUMMY_API_KEY = "mock-api-key";

  @Mock private ConfigurationParser<JsonNode> mockConfigParser;
  @Mock private EppoConfigurationClient mockConfigClient;

  // Tracks the last built client so tearDown can stop its polling timer.
  private BaseAndroidEppoClient<JsonNode> lastClient;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() {
    if (lastClient != null) {
      lastClient.pausePolling();
      lastClient = null;
    }
  }

  /**
   * Builds a client in offline mode with optional polling enabled.
   *
   * @param pollingEnabled whether to enable polling
   * @param pollingIntervalMs polling interval in milliseconds (ignored when pollingEnabled=false)
   * @return initialized EppoClient
   */
  private BaseAndroidEppoClient<JsonNode> buildOfflineClient(
      boolean pollingEnabled, long pollingIntervalMs)
      throws ExecutionException, InterruptedException {
    CompletableFuture<Configuration> initialConfig =
        CompletableFuture.completedFuture(Configuration.emptyConfig());

    BaseAndroidEppoClient.Builder<JsonNode> builder =
        new BaseAndroidEppoClient.Builder<>(
                DUMMY_API_KEY,
                ApplicationProvider.getApplicationContext(),
                mockConfigParser,
                mockConfigClient)
            .forceReinitialize(true)
            .offlineMode(true)
            .initialConfiguration(initialConfig)
            .pollingEnabled(pollingEnabled)
            .isGracefulMode(true);

    if (pollingEnabled) {
      builder.pollingIntervalMs(pollingIntervalMs);
    }

    lastClient = builder.buildAndInitAsync().get();
    return lastClient;
  }

  @Test
  public void testPauseAndResumePolling() throws ExecutionException, InterruptedException {
    // Use non-offline mode with a short interval so we can observe actual polling calls.
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));

    CompletableFuture<Configuration> initialConfig =
        CompletableFuture.completedFuture(Configuration.emptyConfig());

    lastClient =
        new BaseAndroidEppoClient.Builder<>(
                DUMMY_API_KEY,
                ApplicationProvider.getApplicationContext(),
                mockConfigParser,
                mockConfigClient)
            .forceReinitialize(true)
            .initialConfiguration(initialConfig)
            .pollingEnabled(true)
            .pollingIntervalMs(50)
            .isGracefulMode(true)
            .buildAndInitAsync()
            .get();

    assertNotNull("Client should be initialized", lastClient);

    // Wait for at least one polling cycle to fire (50ms interval, wait 150ms).
    Thread.sleep(150);
    verify(mockConfigClient, atLeastOnce()).execute(any(EppoConfigurationRequest.class));

    // Pause: stopPolling() calls cancel(false), which does not interrupt a task already running
    // on the executor thread. At most one in-flight invocation can complete after pausePolling()
    // returns, so we tolerate atMost(1) rather than never().
    lastClient.pausePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(200); // wait 4 intervals — polling must be stopped
    verify(mockConfigClient, atMost(1)).execute(any(EppoConfigurationRequest.class));

    // Resume: polling fires again within one interval.
    lastClient.resumePolling();
    Thread.sleep(150);
    verify(mockConfigClient, atLeastOnce()).execute(any(EppoConfigurationRequest.class));

    lastClient.pausePolling();
  }

  @Test
  public void testResumePollingWithoutStarting() throws ExecutionException, InterruptedException {
    BaseAndroidEppoClient<JsonNode> androidBaseClient = buildOfflineClient(false, 0);
    assertNotNull("Client should be initialized", androidBaseClient);

    // resumePolling() logs a warning when polling interval was not set and does not start polling.
    androidBaseClient.resumePolling();

    Thread.sleep(50);
  }

  @Test
  public void testMultiplePauseResumeCyclesWithVerification()
      throws ExecutionException, InterruptedException {
    // Stub the mock so polling calls don't NPE.
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));

    BaseAndroidEppoClient<JsonNode> androidBaseClient = buildOfflineClient(true, 50);
    assertNotNull("Client should be initialized", androidBaseClient);

    // Let polling fire at least once.
    Thread.sleep(150);
    verify(mockConfigClient, atLeastOnce()).execute(any(EppoConfigurationRequest.class));

    // Pause and verify polling stops.
    androidBaseClient.pausePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(200);
    verify(mockConfigClient, atMost(1)).execute(any(EppoConfigurationRequest.class));

    // Double pause is safe.
    androidBaseClient.pausePolling();

    // Resume and verify polling fires again.
    androidBaseClient.resumePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(150);
    verify(mockConfigClient, atLeastOnce()).execute(any(EppoConfigurationRequest.class));

    // Double resume is safe.
    androidBaseClient.resumePolling();
    Thread.sleep(50);

    // Second pause/resume cycle.
    androidBaseClient.pausePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(150);
    verify(mockConfigClient, atMost(1)).execute(any(EppoConfigurationRequest.class));

    androidBaseClient.resumePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(150);
    verify(mockConfigClient, atLeastOnce()).execute(any(EppoConfigurationRequest.class));

    androidBaseClient.pausePolling();
  }

  @Test
  public void testPollingNotEnabledAndResume() throws ExecutionException, InterruptedException {
    BaseAndroidEppoClient<JsonNode> androidBaseClient = buildOfflineClient(false, 0);

    // Pause should be safe even if not polling
    androidBaseClient.pausePolling();
    Thread.sleep(50);

    // resumePolling() logs a warning when polling interval was not set and does not start polling.
    androidBaseClient.resumePolling();
    Thread.sleep(50);

    // Multiple calls should all be safe
    androidBaseClient.pausePolling();
    androidBaseClient.resumePolling();
    Thread.sleep(50);
  }

  @Test
  public void testPauseAfterInitStopsPolling() throws ExecutionException, InterruptedException {
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));

    BaseAndroidEppoClient<JsonNode> androidBaseClient = buildOfflineClient(true, 50);

    // Immediately pause after initialization — no polls should fire.
    androidBaseClient.pausePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(200);
    verify(mockConfigClient, atMost(1)).execute(any(EppoConfigurationRequest.class));

    // Resume and verify polling fires.
    androidBaseClient.resumePolling();
    reset(mockConfigClient);
    when(mockConfigClient.execute(any(EppoConfigurationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(EppoConfigurationResponse.error(503, null)));
    Thread.sleep(150);
    verify(mockConfigClient, atLeastOnce()).execute(any(EppoConfigurationRequest.class));

    androidBaseClient.pausePolling();
  }
}
