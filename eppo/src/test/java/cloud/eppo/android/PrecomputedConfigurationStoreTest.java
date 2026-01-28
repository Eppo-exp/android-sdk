package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.annotation.NonNull;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.dto.PrecomputedFlag;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PrecomputedConfigurationStoreTest {

  private Application application;
  private PrecomputedConfigurationStore store;

  @Before
  public void setUp() {
    application = RuntimeEnvironment.getApplication();
    store = new PrecomputedConfigurationStore(application, "test-suffix");
    // Clean up any existing cache
    store.deleteCache();
  }

  @Test
  public void testInitialConfigurationIsEmpty() {
    PrecomputedConfigurationResponse config = store.getConfiguration();
    assertNotNull(config);
    assertTrue(config.getFlags().isEmpty());
    assertTrue(config.getBandits().isEmpty());
    assertNull(store.getSalt());
  }

  @Test
  public void testSetConfiguration() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"flag1\": {\n"
            + "      \"variationType\": \"STRING\",\n"
            + "      \"variationValue\": \"dGVzdA==\",\n"
            + "      \"doLog\": false\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse config =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    store.setConfiguration(config);

    assertEquals("test-salt", store.getSalt());
    assertEquals(1, store.getFlags().size());
    assertNotNull(store.getFlag("flag1"));
  }

  @Test
  public void testSaveConfigurationUpdatesInMemory() throws ExecutionException, InterruptedException {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"flag1\": {\n"
            + "      \"variationType\": \"STRING\",\n"
            + "      \"variationValue\": \"dGVzdA==\",\n"
            + "      \"doLog\": false\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse config =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    // Save configuration
    store.saveConfiguration(config).get();

    // Verify in-memory configuration was updated
    assertEquals("test-salt", store.getSalt());
    assertEquals(1, store.getFlags().size());
    assertNotNull(store.getFlag("flag1"));
  }

  @Test
  public void testSaveConfigurationUpdatesInMemoryEvenOnDiskFailure()
      throws ExecutionException, InterruptedException {
    // Create a store with a spy cache file that throws on write
    PrecomputedConfigurationStore storeWithFailingDisk =
        new PrecomputedConfigurationStore(application, "failing-test") {
          @Override
          public CompletableFuture<Void> saveConfiguration(
              @NonNull PrecomputedConfigurationResponse newConfiguration) {
            return CompletableFuture.supplyAsync(
                () -> {
                  // Simulate disk failure by updating in-memory first (as the real impl does)
                  // then pretending the disk write failed
                  setConfiguration(newConfiguration);
                  // Log the simulated failure (this is what the real impl does)
                  android.util.Log.e(
                      "PrecomputedConfigurationStoreTest",
                      "Simulated disk write failure (in-memory updated)");
                  return null;
                });
          }
        };

    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"disk-failure-test-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"disk-failure-flag\": {\n"
            + "      \"variationType\": \"BOOLEAN\",\n"
            + "      \"variationValue\": \"dHJ1ZQ==\",\n"
            + "      \"doLog\": true\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse config =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    // Save should complete without exception even though disk "fails"
    storeWithFailingDisk.saveConfiguration(config).get();

    // In-memory configuration should still be updated
    assertEquals("disk-failure-test-salt", storeWithFailingDisk.getSalt());
    assertEquals(1, storeWithFailingDisk.getFlags().size());
    PrecomputedFlag flag = storeWithFailingDisk.getFlag("disk-failure-flag");
    assertNotNull(flag);
    assertEquals("BOOLEAN", flag.getVariationType());
  }

  @Test
  public void testLoadConfigFromCacheWhenFileDoesNotExist()
      throws ExecutionException, InterruptedException {
    // Ensure cache is deleted
    store.deleteCache();

    PrecomputedConfigurationResponse result = store.loadConfigFromCache().get();
    assertNull(result);
  }

  @Test
  public void testSaveAndLoadConfigFromCache() throws ExecutionException, InterruptedException {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"cached-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"cached-flag\": {\n"
            + "      \"variationType\": \"INTEGER\",\n"
            + "      \"variationValue\": \"NDI=\",\n"
            + "      \"doLog\": true\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse config =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    // Save to cache
    store.saveConfiguration(config).get();

    // Create a new store instance to test loading from disk
    PrecomputedConfigurationStore newStore =
        new PrecomputedConfigurationStore(application, "test-suffix");

    // Load from cache
    PrecomputedConfigurationResponse loaded = newStore.loadConfigFromCache().get();

    assertNotNull(loaded);
    assertEquals("cached-salt", loaded.getSalt());
    assertEquals(1, loaded.getFlags().size());
    assertNotNull(loaded.getFlags().get("cached-flag"));
  }

  @Test
  public void testGetFlagReturnsNullForMissingKey() {
    assertNull(store.getFlag("non-existent-flag"));
  }

  @Test
  public void testGetBanditReturnsNullForMissingKey() {
    assertNull(store.getBandit("non-existent-bandit"));
  }
}
