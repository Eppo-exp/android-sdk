package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.api.Configuration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class EppoClientConfigurationCallbackTest {
  @Test
  public void forceReinitializeUnsubscribesReplacedClientsCallback() throws Exception {
    Application app = RuntimeEnvironment.getApplication();
    FileBackedConfigStore<Configuration> store =
        new FileBackedConfigStore<>(app, "callback-test", new ConfigurationCodec.Default());
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch notified = new CountDownLatch(1);
    Consumer<Configuration> callback =
        config -> {
          calls.incrementAndGet();
          notified.countDown();
        };
    EppoClient previous = null;
    for (int i = 0; i < 2; i++) {
      EppoClient client =
          new EppoClient.Builder("mock-api-key", app)
              .forceReinitialize(true)
              .offlineMode(true)
              .ignoreCachedConfiguration(true)
              .configStore(store)
              .onConfigurationChange(callback)
              .buildAndInitAsync()
              .get(5, TimeUnit.SECONDS);
      if (previous != null) {
        assertFalse(previous.unsubscribeFromConfigurationChange(callback));
      }
      previous = client;
    }
    store.saveConfiguration(Configuration.emptyConfig()).get(5, TimeUnit.SECONDS);
    assertTrue(notified.await(5, TimeUnit.SECONDS));
    assertEquals(1, calls.get());
  }
}
