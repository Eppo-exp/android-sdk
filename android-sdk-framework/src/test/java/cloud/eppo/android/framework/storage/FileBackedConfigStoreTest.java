package cloud.eppo.android.framework.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import cloud.eppo.api.Configuration;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link FileBackedConfigStore}. */
@RunWith(RobolectricTestRunner.class)
public class FileBackedConfigStoreTest {

  @Rule public final TestName testName = new TestName();

  private Application application;
  private ConfigurationCodec<Configuration> codec;
  private String cacheFileSuffix;

  @Before
  public void setUp() {
    application = RuntimeEnvironment.getApplication();
    codec = new ConfigurationCodec.Default();
    cacheFileSuffix = "test-" + testName.getMethodName();
    new ConfigCacheFile(application, cacheFileSuffix, codec.getContentType()).delete();
  }

  @After
  public void tearDown() {
    new ConfigCacheFile(application, cacheFileSuffix, codec.getContentType()).delete();
  }

  @Test
  public void construct_withValidArgs_succeeds() {
    FileBackedConfigStore<Configuration> store =
        new FileBackedConfigStore<>(application, cacheFileSuffix, codec);

    assertNotNull(store);
  }

  @Test
  public void getConfiguration_beforeAnySave_returnsEmptyConfig() {
    FileBackedConfigStore<Configuration> store =
        new FileBackedConfigStore<>(application, cacheFileSuffix, codec);

    Configuration config = store.getConfiguration();

    assertNotNull(config);
    assertEquals(Configuration.emptyConfig(), config);
  }

  @Test
  public void saveConfiguration_thenGetConfiguration_returnsSavedConfig() throws Exception {
    FileBackedConfigStore<Configuration> store =
        new FileBackedConfigStore<>(application, cacheFileSuffix, codec);
    Configuration toSave = Configuration.emptyConfig();

    store.saveConfiguration(toSave).get(5, TimeUnit.SECONDS);

    assertEquals(toSave, store.getConfiguration());
  }

  @Test
  public void loadFromStorage_whenNothingSaved_returnsNull() throws Exception {
    FileBackedConfigStore<Configuration> store =
        new FileBackedConfigStore<>(application, cacheFileSuffix, codec);

    Configuration loaded = store.loadFromStorage().get(5, TimeUnit.SECONDS);

    assertNull(loaded);
  }

  @Test
  public void saveConfiguration_thenLoadFromStorage_returnsSameConfig() throws Exception {
    FileBackedConfigStore<Configuration> store =
        new FileBackedConfigStore<>(application, cacheFileSuffix, codec);
    Configuration toSave = Configuration.emptyConfig();

    store.saveConfiguration(toSave).get(5, TimeUnit.SECONDS);
    Configuration loaded = store.loadFromStorage().get(5, TimeUnit.SECONDS);

    assertNotNull(loaded);
    assertEquals(toSave, loaded);
  }
}
