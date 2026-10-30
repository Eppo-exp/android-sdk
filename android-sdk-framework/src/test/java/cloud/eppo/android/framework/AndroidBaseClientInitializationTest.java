package cloud.eppo.android.framework;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import cloud.eppo.android.framework.exceptions.NotInitializedException;
import cloud.eppo.android.framework.storage.*;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.parser.ConfigurationParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;

@RunWith(RobolectricTestRunner.class)
public class AndroidBaseClientInitializationTest {
  private static final List<TestClient> clients = new ArrayList<>();
  private Application application;

  @Before
  public void setUp() throws Exception {
    application = RuntimeEnvironment.getApplication();
    for (String name : new String[] {"instance", "initialization"}) {
      Field field = AndroidBaseClient.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(null, null);
    }
    clients.clear();
  }

  @After
  public void tearDown() {
    for (TestClient client : clients) client.stopPolling();
  }

  private TestBuilder builder(CompletableFuture<Void> loading) {
    return new TestBuilder(application, loading);
  }

  @Test
  public void repeatedBuildersWaitAndShareOneClient() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    CompletableFuture<TestClient> first = builder(loading).buildAndInitAsync();
    CompletableFuture<TestClient> second = builder(new CompletableFuture<>()).buildAndInitAsync();
    assertEquals(1, clients.size());
    assertFalse(first.isDone());
    assertFalse(second.isDone());
    assertThrows(NotInitializedException.class, AndroidBaseClient::getInstance);
    loading.complete(null);
    assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
    assertSame(first.get(), AndroidBaseClient.getInstance());
  }

  @Test
  public void concurrentCallersConstructOnlyOnce() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    ExecutorService workers = Executors.newFixedThreadPool(2);
    CyclicBarrier start = new CyclicBarrier(2);
    try {
      Callable<CompletableFuture<TestClient>> task =
          () -> {
            start.await(5, TimeUnit.SECONDS);
            return builder(loading).buildAndInitAsync();
          };
      Future<CompletableFuture<TestClient>> a = workers.submit(task);
      Future<CompletableFuture<TestClient>> b = workers.submit(task);
      CompletableFuture<TestClient> first = a.get(5, TimeUnit.SECONDS);
      CompletableFuture<TestClient> second = b.get(5, TimeUnit.SECONDS);
      assertEquals(1, clients.size());
      assertFalse(first.isDone());
      assertFalse(second.isDone());
      loading.complete(null);
      assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  public void cancellingOneCallerDoesNotCancelSharedAttempt() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    CompletableFuture<TestClient> first = builder(loading).buildAndInitAsync();
    CompletableFuture<TestClient> second = builder(loading).buildAndInitAsync();
    first.cancel(true);
    loading.complete(null);
    assertNotNull(second.get(5, TimeUnit.SECONDS));
    assertTrue(first.isCancelled());
  }

  @Test
  public void strictFailureCompletesWaitersAndAllowsRetry() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    CompletableFuture<TestClient> first = builder(loading).buildAndInitAsync();
    CompletableFuture<TestClient> second = builder(loading).buildAndInitAsync();
    loading.completeExceptionally(new IllegalStateException("fetch failed"));
    assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
    assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
    assertThrows(NotInitializedException.class, AndroidBaseClient::getInstance);
    TestClient replacement =
        builder(CompletableFuture.completedFuture(null))
            .buildAndInitAsync()
            .get(5, TimeUnit.SECONDS);
    assertEquals(2, clients.size());
    assertSame(replacement, AndroidBaseClient.getInstance());
  }

  @Test
  public void gracefulFailurePublishesFallback() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    CompletableFuture<TestClient> first = builder(loading).isGracefulMode(true).buildAndInitAsync();
    loading.completeExceptionally(new IllegalStateException("fetch failed"));
    TestClient client = first.get(5, TimeUnit.SECONDS);
    assertSame(client, builder(new CompletableFuture<>()).buildAndInitAsync().get());
  }

  @Test
  public void constructorFailureAllowsRetry() throws Exception {
    TestBuilder broken = builder(new CompletableFuture<>());
    broken.failConstruction = true;
    assertThrows(
        ExecutionException.class, () -> broken.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    assertNotNull(builder(CompletableFuture.completedFuture(null)).buildAndInitAsync().get());
  }

  @Test
  public void synchronousLoadingFailureCleansUpAndAllowsRetry() throws Exception {
    TestBuilder broken = builder(new CompletableFuture<>());
    broken.failLoading = true;
    assertThrows(
        ExecutionException.class, () -> broken.buildAndInitAsync().get(5, TimeUnit.SECONDS));
    assertTrue(clients.get(0).stops > 0);
    assertNotNull(builder(CompletableFuture.completedFuture(null)).buildAndInitAsync().get());
  }

  @Test
  public void forcedReplacementCannotBeOverwrittenByLateSuccess() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    CompletableFuture<TestClient> first = builder(loading).pollingEnabled(true).buildAndInitAsync();
    TestClient old = clients.get(0);
    TestClient replacement =
        builder(CompletableFuture.completedFuture(null))
            .forceReinitialize(true)
            .pollingEnabled(true)
            .buildAndInitAsync()
            .get();
    assertTrue(first.isCompletedExceptionally());
    loading.complete(null);
    assertSame(replacement, AndroidBaseClient.getInstance());
    assertEquals(0, old.starts);
    assertFalse(old.polling);
    assertTrue(replacement.polling);
  }

  @Test
  public void lateFailureDoesNotClearReplacement() throws Exception {
    CompletableFuture<Void> loading = new CompletableFuture<>();
    CompletableFuture<TestClient> first = builder(loading).buildAndInitAsync();
    TestClient replacement =
        builder(CompletableFuture.completedFuture(null))
            .forceReinitialize(true)
            .buildAndInitAsync()
            .get();
    loading.completeExceptionally(new IllegalStateException("old failure"));
    assertTrue(first.isCompletedExceptionally());
    assertSame(replacement, AndroidBaseClient.getInstance());
  }

  @Test
  public void reentrantPollingReplacementDoesNotPublishOldClient() throws Exception {
    AtomicReference<TestClient> replacement = new AtomicReference<>();
    TestBuilder first = builder(CompletableFuture.completedFuture(null));
    first.onStart =
        () ->
            replacement.set(
                builder(CompletableFuture.completedFuture(null))
                    .forceReinitialize(true)
                    .pollingEnabled(true)
                    .buildAndInitAsync()
                    .join());
    CompletableFuture<TestClient> result = first.pollingEnabled(true).buildAndInitAsync();
    assertTrue(result.isCompletedExceptionally());
    assertSame(replacement.get(), AndroidBaseClient.getInstance());
    assertFalse(clients.get(0).polling);
    assertTrue(replacement.get().polling);
  }

  @Test
  public void supersededCallbackCanJoinReplacement() throws Exception {
    CompletableFuture<TestClient> first = builder(new CompletableFuture<>()).buildAndInitAsync();
    AtomicReference<AndroidBaseClient<Configuration, Object>> joined = new AtomicReference<>();
    first.whenComplete(
        (client, failure) ->
            joined.set(builder(CompletableFuture.completedFuture(null)).buildAndInit()));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      TestClient replacement =
          executor
              .submit(
                  () ->
                      builder(CompletableFuture.completedFuture(null))
                          .forceReinitialize(true)
                          .buildAndInitAsync()
                          .get())
              .get(5, TimeUnit.SECONDS);
      assertSame(replacement, joined.get());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void differentBuilderRequiresExplicitReplacement() throws Exception {
    builder(CompletableFuture.completedFuture(null)).buildAndInitAsync().get();
    OtherBuilder other = new OtherBuilder(application);
    assertThrows(ExecutionException.class, () -> other.buildAndInitAsync().get());
    assertNotNull(other.forceReinitialize(true).buildAndInitAsync().get());
  }

  @Test
  public void interruptedSyncWaiterThrowsAndPreservesInterrupt() throws Exception {
    CountDownLatch loadingStarted = new CountDownLatch(1);
    TestBuilder first = builder(new CompletableFuture<>());
    first.onLoad = loadingStarted::countDown;
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean interrupted = new AtomicBoolean();
    Thread waiter =
        new Thread(
            () -> {
              try {
                first.buildAndInit();
              } catch (Throwable e) {
                failure.set(e);
                interrupted.set(Thread.currentThread().isInterrupted());
              }
            });
    waiter.start();
    assertTrue(loadingStarted.await(5, TimeUnit.SECONDS));
    waiter.interrupt();
    waiter.join(5000);
    assertFalse(waiter.isAlive());
    assertNotNull(failure.get());
    assertTrue(interrupted.get());
    TestClient replacement =
        builder(CompletableFuture.completedFuture(null))
            .forceReinitialize(true)
            .buildAndInitAsync()
            .get();
    assertSame(replacement, AndroidBaseClient.getInstance());
  }

  @Test
  public void offlineWithoutCacheCompletes() throws Exception {
    assertNotNull(
        builder(new CompletableFuture<>())
            .offlineMode(true)
            .buildAndInitAsync()
            .get(5, TimeUnit.SECONDS));
  }

  private static class TestClient extends AndroidBaseClient<Configuration, Object> {
    final CompletableFuture<Void> loading;
    final boolean failLoading;
    final Runnable onLoad;
    final Runnable onStart;
    int starts, stops;
    boolean polling;

    TestClient(
        String key,
        String name,
        String version,
        String url,
        AssignmentLogger logger,
        CachingConfigurationStore<Configuration> store,
        boolean graceful,
        boolean obfuscated,
        CompletableFuture<Configuration> initial,
        IAssignmentCache cache,
        ConfigurationParser<Configuration, Object> parser,
        EppoConfigurationClient http,
        TestBuilder builder) {
      super(
          key,
          name,
          version,
          url,
          logger,
          store,
          graceful,
          obfuscated,
          initial,
          cache,
          parser,
          http);
      loading = builder.loading;
      failLoading = builder.failLoading;
      onLoad = builder.onLoad;
      onStart = builder.onStart;
    }

    @Override
    public CompletableFuture<Void> loadConfigurationAsync() {
      if (onLoad != null) onLoad.run();
      if (failLoading) throw new IllegalStateException("synchronous loading failure");
      return loading;
    }

    @Override
    protected void startPolling(long interval, long jitter) {
      starts++;
      polling = true;
      if (onStart != null) onStart.run();
    }

    @Override
    protected void stopPolling() {
      stops++;
      polling = false;
    }
  }

  private static class TestBuilder
      extends AndroidBaseClient.Builder<TestBuilder, TestClient, Configuration, Object> {
    final CompletableFuture<Void> loading;
    boolean failConstruction, failLoading;
    Runnable onLoad, onStart;

    TestBuilder(Application app, CompletableFuture<Void> loading) {
      this(TestBuilder.class, app, loading);
    }

    @SuppressWarnings("unchecked")
    TestBuilder(Class<TestBuilder> type, Application app, CompletableFuture<Void> loading) {
      super(
          type,
          "dummy-key",
          app,
          mock(ConfigurationParser.class),
          new FileBackedConfigStore<>(app, "initialization-test", new ConfigurationCodec.Default()),
          mock(EppoConfigurationClient.class));
      this.loading = loading;
      ignoreCachedConfiguration(true);
      isGracefulMode(false);
    }

    @Override
    protected TestClient newInstance(
        String key,
        String name,
        String version,
        String url,
        AssignmentLogger logger,
        CachingConfigurationStore<Configuration> store,
        boolean graceful,
        boolean obfuscated,
        CompletableFuture<Configuration> initial,
        IAssignmentCache cache,
        ConfigurationParser<Configuration, Object> parser,
        EppoConfigurationClient http) {
      if (failConstruction) throw new IllegalStateException("construction failed");
      TestClient client =
          new TestClient(
              key,
              name,
              version,
              url,
              logger,
              store,
              graceful,
              obfuscated,
              initial,
              cache,
              parser,
              http,
              this);
      clients.add(client);
      return client;
    }
  }

  private static class OtherBuilder extends TestBuilder {
    @SuppressWarnings({"unchecked", "rawtypes"})
    OtherBuilder(Application app) {
      super((Class) OtherBuilder.class, app, CompletableFuture.completedFuture(null));
    }
  }
}
