package cloud.eppo.android.framework;

import static cloud.eppo.android.framework.util.Utils.logTag;
import static cloud.eppo.android.framework.util.Utils.safeCacheKey;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import cloud.eppo.android.framework.storage.CachingConfigurationStore;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.api.Configuration;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.parser.ConfigurationParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for EppoClient polling pause/resume functionality.
 *
 * <p>These tests use offline mode to avoid needing to mock complex configuration loading behavior.
 * They focus on verifying that pausePolling() and resumePolling() can be called safely in various
 * sequences.
 */
public class EppoClientPollingTest {
  private static final String TAG = logTag(EppoClientPollingTest.class);
  private static final String DUMMY_API_KEY = "mock-api-key";

  @Mock
  private ConfigurationParser<Configuration, Configuration.Builder, JsonNode> mockConfigParser;

  @Mock private EppoConfigurationClient mockConfigClient;

  private CachingConfigurationStore<Configuration> configurationStore =
      new FileBackedConfigStore<>(
          ApplicationProvider.getApplicationContext(),
          safeCacheKey(DUMMY_API_KEY),
          new ConfigurationCodec.Default());

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  private static class TestBuilder
      extends AndroidBaseClient.Builder<
          TestBuilder, Configuration, Configuration.Builder, JsonNode> {
    protected TestBuilder(
        @NotNull String apiKey,
        @NotNull Application application,
        @NotNull ConfigurationParser<Configuration, Configuration.Builder, JsonNode> configurationParser,
        @NotNull CachingConfigurationStore<Configuration> configStore,
        @NotNull EppoConfigurationClient configurationClient) {
      super(
          TestBuilder.class,
          apiKey,
          application,
          configurationParser,
          configStore,
          configurationClient);
    }
  }

  /**
   * Builds a client in offline mode with polling enabled.
   *
   * @param pollingIntervalMs Polling interval in milliseconds
   * @return Initialized EppoClient
   */
  private AndroidBaseClient<Configuration, Configuration.Builder, JsonNode>
      buildOfflineClientWithPolling(long pollingIntervalMs)
          throws ExecutionException, InterruptedException {
    // Use an empty configuration for offline mode
    CompletableFuture<Configuration> initialConfig =
        CompletableFuture.completedFuture(Configuration.emptyConfig());

    return new TestBuilder(
            DUMMY_API_KEY,
            ApplicationProvider.getApplicationContext(),
            mockConfigParser,
            configurationStore,
            mockConfigClient)
        .forceReinitialize(true)
        .offlineMode(true)
        .initialConfiguration(initialConfig)
        .pollingEnabled(true)
        .pollingIntervalMs(pollingIntervalMs)
        .isGracefulMode(true) // Enable graceful mode to handle initialization issues
        .buildAndInitAsync()
        .get();
  }

  /**
   * Builds a client in offline mode without polling enabled.
   *
   * @return Initialized EppoClient
   */
  private AndroidBaseClient<Configuration, Configuration.Builder, JsonNode>
      buildOfflineClientWithoutPolling() throws ExecutionException, InterruptedException {
    CompletableFuture<Configuration> initialConfig =
        CompletableFuture.completedFuture(Configuration.emptyConfig());

    return new TestBuilder(
            DUMMY_API_KEY,
            ApplicationProvider.getApplicationContext(),
            mockConfigParser,
            configurationStore,
            mockConfigClient)
        .forceReinitialize(true)
        .offlineMode(true)
        .initialConfiguration(initialConfig)
        .pollingEnabled(false)
        .isGracefulMode(true) // Enable graceful mode to handle initialization issues
        .buildAndInitAsync()
        .get();
  }

  @Test
  public void testPauseAndResumePolling() throws ExecutionException, InterruptedException {
    AndroidBaseClient<Configuration, Configuration.Builder, JsonNode> androidBaseClient =
        buildOfflineClientWithPolling(100);
    assertNotNull("Client should be initialized", androidBaseClient);

    // Test pause
    androidBaseClient.pausePolling();
    Log.d(TAG, "Polling paused");

    // Wait a bit to ensure no crashes
    Thread.sleep(50);

    // Test resume
    androidBaseClient.resumePolling();
    Log.d(TAG, "Polling resumed");

    // Wait a bit to ensure no crashes
    Thread.sleep(50);

    // Final pause for cleanup
    androidBaseClient.pausePolling();
  }

  @Test
  public void testResumePollingWithoutStarting() throws ExecutionException, InterruptedException {
    AndroidBaseClient<Configuration, Configuration.Builder, JsonNode> androidBaseClient =
        buildOfflineClientWithoutPolling();
    assertNotNull("Client should be initialized", androidBaseClient);

    // Try to resume polling (should log warning and not crash per EppoClient.java:436-441)
    androidBaseClient.resumePolling();
    Log.d(TAG, "Resume called without starting - should log warning");

    // Wait a bit to ensure no crashes
    Thread.sleep(50);

    // Should not crash or throw exception
  }

  @Test
  public void testMultiplePauseResumeCycles() throws ExecutionException, InterruptedException {
    AndroidBaseClient<Configuration, Configuration.Builder, JsonNode> androidBaseClient =
        buildOfflineClientWithPolling(100);
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

    // Third cycle
    androidBaseClient.pausePolling();
    Log.d(TAG, "Third pause");
    Thread.sleep(50);
    androidBaseClient.resumePolling();
    Log.d(TAG, "Third resume");
    Thread.sleep(50);

    // Final cleanup
    androidBaseClient.pausePolling();
  }

  @Test
  public void testPauseResumeSequenceDoesNotCrash()
      throws ExecutionException, InterruptedException {
    AndroidBaseClient<Configuration, Configuration.Builder, JsonNode> androidBaseClient =
        buildOfflineClientWithPolling(50);

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
    AndroidBaseClient<Configuration, Configuration.Builder, JsonNode> androidBaseClient =
        buildOfflineClientWithoutPolling();

    // Pause should be safe even if not polling
    androidBaseClient.pausePolling();
    Thread.sleep(50);

    // Resume should log warning per EppoClient.java:436-441
    androidBaseClient.resumePolling();
    Thread.sleep(50);

    // Multiple calls should all be safe
    androidBaseClient.pausePolling();
    androidBaseClient.resumePolling();
    Thread.sleep(50);
  }

  @Test
  public void testPauseAfterInitDoesNotCrash() throws ExecutionException, InterruptedException {
    AndroidBaseClient<Configuration, Configuration.Builder, JsonNode> androidBaseClient =
        buildOfflineClientWithPolling(100);

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
