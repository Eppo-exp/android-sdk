package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.api.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class EppoClientOfflineInitTest {
  // Same inputs as AndroidBaseClientInitializationTest#offlineWithoutCacheCompletes.
  @Test(timeout = 10_000)
  public void offlineWithoutAnyConfigurationCompletes() throws Exception {
    EppoClient client =
        new EppoClient.Builder("mock-api-key", RuntimeEnvironment.getApplication())
            .forceReinitialize(true)
            .offlineMode(true)
            .ignoreCachedConfiguration(true)
            .buildAndInitAsync()
            .get(2, TimeUnit.SECONDS);

    assertNotNull(client);
  }

  @Test
  public void forcedRetryReadsRepairedCache() throws Exception {
    TestStore store = new TestStore();
    store.cached = failedRead();
    EppoClient.Builder builder = builder(store);
    assertThrows(
        ExecutionException.class, () -> builder.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    store.cached = CompletableFuture.completedFuture(configuration());
    assertNotNull(builder.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    assertEquals(2, store.reads);
  }

  @Test
  public void forcedRetryHonoursIgnoreCacheAfterImplicitRead() throws Exception {
    TestStore store = new TestStore();
    store.cached = failedRead();
    EppoClient.Builder builder = builder(store);
    assertThrows(
        ExecutionException.class, () -> builder.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    builder.ignoreCachedConfiguration(true);
    assertNotNull(builder.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    assertEquals(1, store.reads);
  }

  @Test
  public void explicitInitialConfigurationSurvivesForcedRetry() throws Exception {
    TestStore store = new TestStore();
    EppoClient.Builder builder =
        builder(store).initialConfiguration(CompletableFuture.completedFuture(configuration()));
    assertNotNull(builder.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    builder.ignoreCachedConfiguration(true);
    assertNotNull(builder.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    assertEquals(0, store.reads);
  }

  private static EppoClient.Builder builder(FileBackedConfigStore<Configuration> store) {
    return new EppoClient.Builder("mock-api-key", RuntimeEnvironment.getApplication())
        .forceReinitialize(true)
        .offlineMode(true)
        .isGracefulMode(false)
        .configStore(store);
  }

  private static final class TestStore extends FileBackedConfigStore<Configuration> {
    CompletableFuture<Configuration> cached;
    int reads;

    TestStore() {
      super(RuntimeEnvironment.getApplication(), "retry-test", new ConfigurationCodec.Default());
    }

    @Override
    public CompletableFuture<Configuration> loadFromStorage() {
      reads++;
      return cached;
    }
  }

  private static CompletableFuture<Configuration> failedRead() {
    CompletableFuture<Configuration> failed = new CompletableFuture<>();
    failed.completeExceptionally(new IllegalStateException("temporary read failure"));
    return failed;
  }

  private static Configuration configuration() {
    String flags =
        "{\"format\":\"SERVER\",\"createdAt\":\"2026-11-27T00:00:00.000Z\","
            + "\"environment\":{\"name\":\"Test\"},\"banditReferences\":{},\"flags\":{"
            + "\"flag\":{\"key\":\"flag\",\"enabled\":true,\"variationType\":\"STRING\","
            + "\"totalShards\":10000,\"variations\":{},\"allocations\":[]}}}";
    JacksonConfigurationParser parser = new JacksonConfigurationParser();
    return parser.buildConfig(
        parser.parseFlagConfig(flags.getBytes(StandardCharsets.UTF_8)), null, null, null);
  }
}
