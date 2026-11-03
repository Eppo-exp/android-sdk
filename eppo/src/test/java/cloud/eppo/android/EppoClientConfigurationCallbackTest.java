package cloud.eppo.android;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.api.Configuration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    for (int i = 0; i < 2; i++) {
      new EppoClient.Builder("mock-api-key", app)
          .forceReinitialize(true)
          .offlineMode(true)
          .ignoreCachedConfiguration(true)
          .configStore(store)
          .onConfigurationChange(config -> calls.incrementAndGet())
          .buildAndInitAsync()
          .get(5, TimeUnit.SECONDS);
    }
    calls.set(0);
    store.saveConfiguration(Configuration.emptyConfig()).get(5, TimeUnit.SECONDS);
    assertEquals(1, calls.get());
  }
}
