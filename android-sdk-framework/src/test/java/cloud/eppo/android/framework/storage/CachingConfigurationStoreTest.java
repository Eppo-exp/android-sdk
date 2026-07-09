package cloud.eppo.android.framework.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.eppo.JacksonConfigurationParser;
import cloud.eppo.api.Configuration;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link CachingConfigurationStore}. */
@RunWith(RobolectricTestRunner.class)
public class CachingConfigurationStoreTest {

  private ByteStore mockByteStore;
  private ConfigurationCodec<Configuration> spyCodec;
  private CachingConfigurationStore<Configuration> testedStore;

  /** One shared non-empty configuration used across tests (built once in setUp). */
  private Configuration sampleConfiguration;

  /** Serialized form of sampleConfiguration from the real codec (for verify when needed). */
  private byte[] sampleConfigurationBytes;

  @Before
  public void setUp() throws Exception {
    mockByteStore = mock(ByteStore.class);
    spyCodec = spy(new ConfigurationCodec.Default());
    testedStore = new CachingConfigurationStore<>(spyCodec, mockByteStore);
    // Parse flags-v1.json from test resources using sdk-common-jvm JacksonConfigurationParser.
    sampleConfiguration = loadSampleConfigurationFromResource();
    ConfigurationCodec<Configuration> realCodec =
        new ConfigurationCodec.Default();
    sampleConfigurationBytes = realCodec.toBytes(sampleConfiguration);
  }

  private static Configuration loadSampleConfigurationFromResource() throws Exception {
    try (InputStream in =
        Objects.requireNonNull(
            CachingConfigurationStoreTest.class.getResourceAsStream("/flags-v1.json"),
            "flags-v1.json not found on test classpath")) {
      byte[] jsonBytes = in.readAllBytes();
      JacksonConfigurationParser parser = new JacksonConfigurationParser();
      return new Configuration.Builder(parser.parseFlagConfig(jsonBytes)).build();
    }
  }

  @Test
  public void testGetConfiguration_returnsEmptyConfigByDefault() {
    Configuration config = testedStore.getConfiguration();
    assertNotNull("Configuration should not be null", config);
    assertEquals("Should return empty config by default", Configuration.emptyConfig(), config);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveConfiguration_nullConfiguration_throwsException() {
    testedStore.saveConfiguration(null);
  }

  @Test
  public void testSaveConfiguration_updatesInMemoryCache() throws Exception {
    when(mockByteStore.write(any())).thenReturn(CompletableFuture.completedFuture(null));

    testedStore.saveConfiguration(sampleConfiguration).get(5, TimeUnit.SECONDS);

    assertEquals(
        "In-memory config should be updated", sampleConfiguration, testedStore.getConfiguration());

    verify(spyCodec, times(1)).toBytes(sampleConfiguration);
    verify(mockByteStore, times(1)).write(sampleConfigurationBytes);
  }

  @Test
  public void testLoadFromStorage_whenExists() throws Exception {

    // Mock IO read
    when(mockByteStore.read())
        .thenReturn(CompletableFuture.completedFuture(sampleConfigurationBytes));

    // Load from storage
    Configuration loaded = testedStore.loadFromStorage().get(5, TimeUnit.SECONDS);

    // Verify loaded config
    assertEquals("Should return stored configuration", sampleConfiguration, loaded);

    // Verify in-memory cache was NOT updated
    assertEquals(
        "In-memory config should still be empty",
        Configuration.emptyConfig(),
        testedStore.getConfiguration());

    // Verify IO and codec were called
    verify(mockByteStore, times(1)).read();
    verify(spyCodec, times(1)).fromBytes(sampleConfigurationBytes);
  }

  @Test
  public void testLoadFromStorage_whenNotExists() throws Exception {
    // Mock IO read returning null (file doesn't exist)
    when(mockByteStore.read()).thenReturn(CompletableFuture.completedFuture(null));

    // Load from storage
    Configuration loaded = testedStore.loadFromStorage().get(5, TimeUnit.SECONDS);

    // Verify null is returned
    assertNull("Should return null when storage doesn't exist", loaded);

    // Verify IO was called but codec was not
    verify(mockByteStore, times(1)).read();
    verify(spyCodec, times(0)).fromBytes(any());
  }

  @Test
  public void testLoadFromStorage_emptyBytes() throws Exception {
    // Mock byte store read returning empty array
    when(mockByteStore.read()).thenReturn(CompletableFuture.completedFuture(new byte[0]));

    // Load from storage
    Configuration loaded = testedStore.loadFromStorage().get(5, TimeUnit.SECONDS);

    // Verify null is returned for empty bytes
    assertNull("Should return null for empty bytes", loaded);

    // Verify IO was called but codec was not
    verify(mockByteStore, times(1)).read();
    verify(spyCodec, times(0)).fromBytes(any());
  }

  @Test
  public void testSaveConfiguration_codecException() {
    Configuration beforeSave = testedStore.getConfiguration();

    // Mock codec to throw exception (may throw synchronously before returning a future)
    when(spyCodec.toBytes(sampleConfiguration))
        .thenThrow(new RuntimeException("Serialization failed"));

    // Save configuration should propagate exception (sync from codec or via ExecutionException)
    try {
      testedStore.saveConfiguration(sampleConfiguration).get(5, TimeUnit.SECONDS);
      fail("Expected exception");
    } catch (ExecutionException e) {
      assertTrue("Should contain RuntimeException", e.getCause() instanceof RuntimeException);
      assertTrue(
          "Should contain error message",
          e.getCause().getMessage().contains("Serialization failed"));
    } catch (Exception e) {
      assertTrue(
          "Should be serialization failure: " + e.getMessage(),
          e.getMessage() != null && e.getMessage().contains("Serialization failed"));
    }

    // Verify in-memory cache was NOT updated
    assertSame(
        "In-memory config should be unchanged after codec failure",
        beforeSave,
        testedStore.getConfiguration());
  }

  @Test
  public void testSaveConfiguration_ioException() {
    Configuration beforeSave = testedStore.getConfiguration();
    byte[] serializedBytes = new byte[] {1, 2, 3, 4};

    // Mock codec serialization
    when(spyCodec.toBytes(sampleConfiguration)).thenReturn(serializedBytes);

    // Mock IO to throw exception
    CompletableFuture<Void> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("Write failed"));
    when(mockByteStore.write(serializedBytes)).thenReturn(failedFuture);

    // Save configuration should propagate exception
    try {
      testedStore.saveConfiguration(sampleConfiguration).get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertTrue("Should contain RuntimeException", e.getCause() instanceof RuntimeException);
      assertTrue(
          "Should contain error message", e.getCause().getMessage().contains("Write failed"));
    } catch (Exception e) {
      fail("Unexpected exception: " + e.getMessage());
    }

    // Verify in-memory cache was NOT updated
    assertSame(
        "In-memory config should be unchanged after IO failure",
        beforeSave,
        testedStore.getConfiguration());
  }

  @Test
  public void testLoadFromStorage_codecException() {
    byte[] storedBytes = new byte[] {1, 2, 3, 4};

    // Mock IO read
    when(mockByteStore.read()).thenReturn(CompletableFuture.completedFuture(storedBytes));

    // Load should propagate exception
    try {
      testedStore.loadFromStorage().get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertTrue("Should contain RuntimeException", e.getCause() instanceof RuntimeException);
      assertEquals("Failed to deserialize configuration", e.getCause().getMessage());
    } catch (Exception e) {
      fail("Unexpected exception: " + e.getMessage());
    }
  }

  @Test
  public void testLoadFromStorage_ioException() {
    // Mock IO to throw exception
    CompletableFuture<byte[]> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("Read failed"));
    when(mockByteStore.read()).thenReturn(failedFuture);

    // Load should propagate exception
    try {
      testedStore.loadFromStorage().get(5, TimeUnit.SECONDS);
      fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      assertTrue("Should contain RuntimeException", e.getCause() instanceof RuntimeException);
      assertTrue("Should contain error message", e.getCause().getMessage().contains("Read failed"));
    } catch (Exception e) {
      fail("Unexpected exception: " + e.getMessage());
    }
  }

  @Test
  public void testThreadSafety_concurrentSaves() throws Exception {
    int numThreads = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numThreads);
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    AtomicInteger errorCount = new AtomicInteger(0);

    // Mock codec and IO for successful operations
    when(mockByteStore.write(any())).thenReturn(CompletableFuture.completedFuture(null));

    // Start multiple threads saving configurations concurrently
    for (int i = 0; i < numThreads; i++) {
      final int threadId = i;
      Thread thread =
          new Thread(
              () -> {
                try {
                  startLatch.await(); // Wait for all threads to be ready
                  Configuration config = Configuration.emptyConfig();
                  CompletableFuture<Void> future = testedStore.saveConfiguration(config);
                  synchronized (futures) {
                    futures.add(future);
                  }
                } catch (Exception e) {
                  errorCount.incrementAndGet();
                  fail("Unexpected exception in thread " + threadId + ": " + e.getMessage());
                } finally {
                  doneLatch.countDown();
                }
              });
      thread.start();
    }

    // Start all threads at once
    startLatch.countDown();

    // Wait for all threads to complete
    doneLatch.await();

    // Wait for all futures to complete
    for (CompletableFuture<Void> future : futures) {
      future.get(5, TimeUnit.SECONDS);
    }

    // Verify all operations completed without errors
    assertEquals("Should have no errors", 0, errorCount.get());
    Configuration finalConfig = testedStore.getConfiguration();
    assertNotNull("Final configuration should not be null", finalConfig);
    assertEquals("Final config should be empty config", Configuration.emptyConfig(), finalConfig);
  }

  @Test
  public void testThreadSafety_concurrentLoadAndSave() throws Exception {
    // Mock byte store
    when(mockByteStore.read())
        .thenReturn(CompletableFuture.completedFuture(sampleConfigurationBytes));

    when(mockByteStore.write(sampleConfigurationBytes))
        .thenReturn(CompletableFuture.completedFuture(null));

    // Run concurrent load and save operations (reduced iterations for performance)
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    AtomicInteger errorCount = new AtomicInteger(0);

    Thread loadThread =
        new Thread(
            () -> {
              try {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                  testedStore.loadFromStorage().get(5, TimeUnit.SECONDS);
                }
              } catch (Exception e) {
                errorCount.incrementAndGet();
                fail("Unexpected exception in load thread: " + e.getMessage());
              } finally {
                doneLatch.countDown();
              }
            });

    Thread saveThread =
        new Thread(
            () -> {
              try {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                  testedStore.saveConfiguration(sampleConfiguration).get(5, TimeUnit.SECONDS);
                }
              } catch (Exception e) {
                errorCount.incrementAndGet();
                fail("Unexpected exception in save thread: " + e.getMessage());
              } finally {
                doneLatch.countDown();
              }
            });

    loadThread.start();
    saveThread.start();
    startLatch.countDown();

    // Wait for completion
    doneLatch.await();

    // Verify no exceptions and store is in valid state
    assertEquals("Should have no errors", 0, errorCount.get());
    assertNotNull("Store should have valid configuration", testedStore.getConfiguration());
  }
}
