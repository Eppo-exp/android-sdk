package cloud.eppo.android;

import static org.junit.Assert.assertNotNull;

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
}
