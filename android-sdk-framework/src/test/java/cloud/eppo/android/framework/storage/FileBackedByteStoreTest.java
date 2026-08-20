package cloud.eppo.android.framework.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit tests for {@link FileBackedByteStore}. */
@RunWith(RobolectricTestRunner.class)
public class FileBackedByteStoreTest {

  private Application application;
  private ConfigCacheFile cacheFile;
  private FileBackedByteStore byteStore;

  @Before
  public void setUp() {
    application = RuntimeEnvironment.getApplication();
    cacheFile = new ConfigCacheFile(application, "test-cache-file", "dat");
    byteStore = new FileBackedByteStore(cacheFile);

    cacheFile.delete();
  }

  @After
  public void tearDown() {
    cacheFile.delete();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConstructorNullCacheFile() {
    new FileBackedByteStore(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWriteNullBytes() {
    byteStore.write(null);
  }

  @Test
  public void testReadNonExistentReturnsNull() throws Exception {
    CompletableFuture<byte[]> future = byteStore.read();
    byte[] result = future.get(5, TimeUnit.SECONDS);
    assertNull("Expected null for non-existent file", result);
  }

  @Test
  public void testWriteThenRead() throws Exception {
    byte[] testData = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    // Write data
    CompletableFuture<Void> writeFuture = byteStore.write(testData);
    writeFuture.get(5, TimeUnit.SECONDS); // Wait for write to complete

    // Read data back
    CompletableFuture<byte[]> readFuture = byteStore.read();
    byte[] result = readFuture.get(5, TimeUnit.SECONDS);

    assertNotNull("Expected non-null result", result);
    assertArrayEquals("Data should match", testData, result);
  }

  @Test
  public void testReadWriteRoundTrip() throws Exception {
    byte[] originalData = "Test data for round trip".getBytes(StandardCharsets.UTF_8);

    // Write
    byteStore.write(originalData).get(5, TimeUnit.SECONDS);

    // Read
    byte[] readData = byteStore.read().get(5, TimeUnit.SECONDS);

    assertNotNull("Read data should not be null", readData);
    assertArrayEquals("Round trip should preserve data", originalData, readData);
  }

  @Test
  public void testAsyncReadWrite() throws Exception {
    byte[] data1 = "First write".getBytes(StandardCharsets.UTF_8);
    byte[] data2 = "Second write".getBytes(StandardCharsets.UTF_8);

    // First write
    byteStore.write(data1).get(5, TimeUnit.SECONDS);
    byte[] read1 = byteStore.read().get(5, TimeUnit.SECONDS);
    assertArrayEquals("First read should match first write", data1, read1);

    // Second write (overwrite)
    byteStore.write(data2).get(5, TimeUnit.SECONDS);
    byte[] read2 = byteStore.read().get(5, TimeUnit.SECONDS);
    assertArrayEquals("Second read should match second write", data2, read2);
  }

  @Test
  public void testOverwriteExistingData() throws Exception {
    byte[] initialData = "Initial content".getBytes(StandardCharsets.UTF_8);
    byte[] newData = "New content".getBytes(StandardCharsets.UTF_8);

    // Write initial data
    byteStore.write(initialData).get(5, TimeUnit.SECONDS);

    // Verify initial data
    byte[] readInitial = byteStore.read().get(5, TimeUnit.SECONDS);
    assertArrayEquals("Initial data should be readable", initialData, readInitial);

    // Overwrite with new data
    byteStore.write(newData).get(5, TimeUnit.SECONDS);

    // Verify new data
    byte[] readNew = byteStore.read().get(5, TimeUnit.SECONDS);
    assertArrayEquals("New data should overwrite old data", newData, readNew);
  }

  @Test
  public void testWriteEmptyByteArray() throws Exception {
    byte[] emptyData = new byte[0];

    // Write empty array
    byteStore.write(emptyData).get(5, TimeUnit.SECONDS);

    // Read back
    byte[] result = byteStore.read().get(5, TimeUnit.SECONDS);
    assertNotNull("Should be able to read empty array", result);
    assertArrayEquals("Empty array should round trip", emptyData, result);
  }

  @Test
  public void testWriteLargeData() throws Exception {
    // Create 1MB of test data
    byte[] largeData = new byte[1024 * 1024];
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    // Write large data
    byteStore.write(largeData).get(10, TimeUnit.SECONDS);

    // Read back
    byte[] result = byteStore.read().get(10, TimeUnit.SECONDS);
    assertNotNull("Should be able to read large data", result);
    assertArrayEquals("Large data should round trip", largeData, result);
  }

  @Test
  public void testReadAfterDelete() throws Exception {
    byte[] testData = "Test data".getBytes(StandardCharsets.UTF_8);

    // Write data
    byteStore.write(testData).get(5, TimeUnit.SECONDS);

    // Verify write
    byte[] read1 = byteStore.read().get(5, TimeUnit.SECONDS);
    assertNotNull("Data should exist", read1);

    // Delete file
    cacheFile.delete();

    // Read after delete should return null
    byte[] read2 = byteStore.read().get(5, TimeUnit.SECONDS);
    assertNull("Read after delete should return null", read2);
  }

  @Test
  public void testConcurrentWrites() throws Exception {
    byte[] data1 = "Data 1".getBytes(StandardCharsets.UTF_8);
    byte[] data2 = "Data 2".getBytes(StandardCharsets.UTF_8);

    // Start two writes concurrently
    CompletableFuture<Void> write1 = byteStore.write(data1);
    CompletableFuture<Void> write2 = byteStore.write(data2);

    // Wait for both to complete
    CompletableFuture.allOf(write1, write2).get(5, TimeUnit.SECONDS);

    // Read result - should be one of the two writes
    byte[] result = byteStore.read().get(5, TimeUnit.SECONDS);
    assertNotNull("Result should not be null", result);
    assertTrue(
        "Result should be one of the written values",
        java.util.Arrays.equals(data1, result) || java.util.Arrays.equals(data2, result));
  }
}
