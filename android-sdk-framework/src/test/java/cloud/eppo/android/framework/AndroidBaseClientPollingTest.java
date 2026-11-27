package cloud.eppo.android.framework;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.app.Application;
import cloud.eppo.android.framework.storage.CachingConfigurationStore;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.http.EppoConfigurationResponse;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.parser.ConfigurationParser;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Counts real configuration fetches made by the polling timer. */
@RunWith(RobolectricTestRunner.class)
public class AndroidBaseClientPollingTest {
  private final AtomicInteger fetches = new AtomicInteger();
  private AndroidBaseClient<Configuration, Object> client;

  @Before
  public void resetSingleton() throws Exception {
    for (String name : new String[] {"instance", "initialization"}) {
      Field field = AndroidBaseClient.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(null, null);
    }
  }

  @After
  public void stopPolling() {
    if (client != null) {
      client.pausePolling();
    }
  }

  @Test
  public void pauseStopsPollingAndResumeRestartsIt() throws Exception {
    client = build(true);
    awaitFetchesAbove(0);
    client.pausePolling();
    Thread.sleep(150);
    int paused = fetches.get();
    Thread.sleep(400);
    assertEquals(paused, fetches.get());
    client.resumePolling();
    awaitFetchesAbove(paused);
  }

  @Test
  public void resumeWithoutPollingDoesNotPoll() throws Exception {
    client = build(false);
    client.resumePolling();
    Thread.sleep(400);
    assertEquals(0, fetches.get());
  }

  private AndroidBaseClient<Configuration, Object> build(boolean polling) throws Exception {
    EppoConfigurationClient http =
        request -> {
          fetches.incrementAndGet();
          return CompletableFuture.completedFuture(EppoConfigurationResponse.notModified(null));
        };
    return new PollingBuilder(RuntimeEnvironment.getApplication(), http)
        .offlineMode(true)
        .forceReinitialize(true)
        .pollingEnabled(polling)
        .pollingIntervalMs(100)
        .pollingJitterMs(10)
        .buildAndInitAsync()
        .get(5, TimeUnit.SECONDS);
  }

  private void awaitFetchesAbove(int count) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 3000;
    while (fetches.get() <= count && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertTrue("expected a poll", fetches.get() > count);
  }

  private static final class PollingBuilder
      extends AndroidBaseClient.Builder<
          PollingBuilder, AndroidBaseClient<Configuration, Object>, Configuration, Object> {
    @SuppressWarnings("unchecked")
    PollingBuilder(Application application, EppoConfigurationClient http) {
      super(
          PollingBuilder.class,
          "dummy-key",
          application,
          mock(ConfigurationParser.class),
          new FileBackedConfigStore<>(
              application, "polling-test", new ConfigurationCodec.Default()),
          http);
      ignoreCachedConfiguration(true);
    }

    @Override
    protected AndroidBaseClient<Configuration, Object> newInstance(
        String apiKey,
        String sdkName,
        String sdkVersion,
        String apiBaseUrl,
        AssignmentLogger assignmentLogger,
        CachingConfigurationStore<Configuration> configurationStore,
        boolean isGracefulMode,
        boolean expectObfuscatedConfig,
        CompletableFuture<Configuration> initialConfiguration,
        IAssignmentCache assignmentCache,
        ConfigurationParser<Configuration, Object> configurationParser,
        EppoConfigurationClient configurationClient) {
      return new AndroidBaseClient<Configuration, Object>(
          apiKey,
          sdkName,
          sdkVersion,
          apiBaseUrl,
          assignmentLogger,
          configurationStore,
          isGracefulMode,
          expectObfuscatedConfig,
          initialConfiguration,
          assignmentCache,
          configurationParser,
          configurationClient) {};
    }
  }
}
