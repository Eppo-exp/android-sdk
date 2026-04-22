package cloud.eppo.android.framework;

import static cloud.eppo.android.framework.util.Utils.logTag;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;
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
  private static final String TAG = logTag(EppoClientPollingTest.class);
  private static final String DUMMY_API_KEY = "mock-api-key";

  @Mock private ConfigurationParser<JsonNode> mockConfigParser;
  @Mock private EppoConfigurationClient mockConfigClient;

  // Tracks the last built client so tearDown can stop its polling timer.
  private BaseAndroidClient<JsonNode> lastClient;

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
  private BaseAndroidClient<JsonNode> buildOfflineClient(
      boolean pollingEnabled, long pollingIntervalMs)
      throws ExecutionException, InterruptedException {
    CompletableFuture<Configuration> initialConfig =
        CompletableFuture.completedFuture(Configuration.emptyConfig());

    BaseAndroidClient.Builder<JsonNode> builder =
        new BaseAndroidClient.Builder<>(
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
        new BaseAndroidClient.Builder<>(
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
    BaseAndroidClient<JsonNode> androidBaseClient = buildOfflineClient(false, 0);
    assertNotNull("Client should be initialized", androidBaseClient);

    // resumePolling() logs a warning when polling interval was not set and does not start polling.
    androidBaseClient.resumePolling();
    Log.d(TAG, "Resume called without starting - should log warning");

    Thread.sleep(50);
  }

  @Test
  public void testMultiplePauseResumeCycles() throws ExecutionException, InterruptedException {
    BaseAndroidClient<JsonNode> androidBaseClient = buildOfflineClient(true, 100);
    assertNotNull("Client should be initialized", androidBaseClient);

    // First cycle
    androidBaseClient.pausePolling();
    Log.d(TAG, "First pause");
    Thread.sleep(50);
    androidBaseClient.resumePolling();
    Log.d(TAG, "First resume");
    Thread.sleep(50);

    // Second cycle
    androidBaseClient.pausePolling();
    Log.d(TAG, "Second pause");
    Thread.sleep(50);
    androidBaseClient.resumePolling();
    Log.d(TAG, "Second resume");
    Thread.sleep(50);

    // Final cleanup
    androidBaseClient.pausePolling();
  }

  @Test
  public void testPauseResumeSequenceDoesNotCrash()
      throws ExecutionException, InterruptedException {
    BaseAndroidClient<JsonNode> androidBaseClient = buildOfflineClient(true, 50);

    // Various sequences that should all work without crashing
    androidBaseClient.pausePolling();
    androidBaseClient.pausePolling(); // Double pause
    Thread.sleep(50);

    androidBaseClient.resumePolling();
    Thread.sleep(50);

    androidBaseClient.resumePolling(); // Double resume
    Thread.sleep(50);

    androidBaseClient.pausePolling();
    androidBaseClient.resumePolling();
    Thread.sleep(50);

    androidBaseClient.pausePolling(); // Final pause for cleanup
  }

  @Test
  public void testPollingNotEnabledAndResume() throws ExecutionException, InterruptedException {
    BaseAndroidClient<JsonNode> androidBaseClient = buildOfflineClient(false, 0);

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
  public void testPauseAfterInitDoesNotCrash() throws ExecutionException, InterruptedException {
    BaseAndroidClient<JsonNode> androidBaseClient = buildOfflineClient(true, 100);

    // Immediately pause after initialization
    androidBaseClient.pausePolling();
    Log.d(TAG, "Paused immediately after init");
    Thread.sleep(200);

    // Resume
    androidBaseClient.resumePolling();
    Thread.sleep(200);

    // Final pause
    androidBaseClient.pausePolling();
  }
}
