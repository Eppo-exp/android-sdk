package cloud.eppo.android.framework.storage;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import cloud.eppo.api.Configuration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class PersistenceOrderingReviewTest {
  private static class DelayedStore implements ByteStore {
    byte[] disk;
    int writes;
    final CompletableFuture<Void> first = new CompletableFuture<>();

    public CompletableFuture<byte[]> read() {
      return CompletableFuture.completedFuture(disk);
    }

    public CompletableFuture<Void> write(byte[] bytes) {
      disk = bytes;
      return ++writes == 1 ? first : CompletableFuture.completedFuture(null);
    }
  }

  @Test
  public void overlappingSavesKeepDiskAndMemoryConsistent() throws Exception {
    Configuration a = mock(Configuration.class);
    Configuration b = mock(Configuration.class);
    ConfigurationCodec<Configuration> codec = mock(ConfigurationCodec.class);
    when(codec.toBytes(a)).thenReturn(new byte[] {1});
    when(codec.toBytes(b)).thenReturn(new byte[] {2});
    when(codec.fromBytes(new byte[] {1})).thenReturn(a);
    when(codec.fromBytes(new byte[] {2})).thenReturn(b);
    DelayedStore bytes = new DelayedStore();
    CachingConfigurationStore<Configuration> store = new CachingConfigurationStore<>(codec, bytes);
    CompletableFuture<Void> first = store.saveConfiguration(a);
    CompletableFuture<Void> second = store.saveConfiguration(b);
    bytes.first.complete(null);
    first.get(5, TimeUnit.SECONDS);
    second.get(5, TimeUnit.SECONDS);
    assertSame(
        "Both successful saves must leave memory matching persisted bytes",
        store.loadFromStorage().get(5, TimeUnit.SECONDS),
        store.getConfiguration());
  }

  @Test
  public void serializationRunsOffCallerThread() throws Exception {
    Thread caller = Thread.currentThread();
    Configuration config = mock(Configuration.class);
    ConfigurationCodec<Configuration> codec = mock(ConfigurationCodec.class);
    when(codec.toBytes(config))
        .thenAnswer(
            invocation -> {
              assertNotSame(
                  "Initial configuration serialization must not run on the caller",
                  caller,
                  Thread.currentThread());
              return new byte[] {1};
            });
    ByteStore bytes = mock(ByteStore.class);
    when(bytes.write(any())).thenReturn(CompletableFuture.completedFuture(null));
    CachingConfigurationStore<Configuration> store = new CachingConfigurationStore<>(codec, bytes);
    store.saveConfiguration(config).get(5, TimeUnit.SECONDS);
    verify(codec).toBytes(config);
  }

  @Test
  public void failedWriteDoesNotPoisonNextSave() throws Exception {
    Configuration a = mock(Configuration.class);
    Configuration b = mock(Configuration.class);
    ConfigurationCodec<Configuration> codec = mock(ConfigurationCodec.class);
    when(codec.toBytes(a)).thenReturn(new byte[] {1});
    when(codec.toBytes(b)).thenReturn(new byte[] {2});
    DelayedStore bytes = new DelayedStore();
    CachingConfigurationStore<Configuration> store = new CachingConfigurationStore<>(codec, bytes);
    CompletableFuture<Void> first = store.saveConfiguration(a);
    CompletableFuture<Void> second = store.saveConfiguration(b);
    bytes.first.completeExceptionally(new IllegalStateException("injected write failure"));
    try {
      first.get(5, TimeUnit.SECONDS);
      fail("Expected injected failure");
    } catch (java.util.concurrent.ExecutionException expected) {
      assertTrue(expected.getCause() instanceof IllegalStateException);
    }
    second.get(5, TimeUnit.SECONDS);
    assertSame(b, store.getConfiguration());
    assertArrayEquals(new byte[] {2}, bytes.disk);
  }

  @Test
  public void codecFailureDoesNotPoisonNextSave() throws Exception {
    Configuration a = mock(Configuration.class);
    Configuration b = mock(Configuration.class);
    ConfigurationCodec<Configuration> codec = mock(ConfigurationCodec.class);
    when(codec.toBytes(a)).thenThrow(new IllegalStateException("injected codec failure"));
    when(codec.toBytes(b)).thenReturn(new byte[] {2});
    ByteStore bytes = mock(ByteStore.class);
    when(bytes.write(any())).thenReturn(CompletableFuture.completedFuture(null));
    CachingConfigurationStore<Configuration> store = new CachingConfigurationStore<>(codec, bytes);
    CompletableFuture<Void> first = store.saveConfiguration(a);
    CompletableFuture<Void> second = store.saveConfiguration(b);
    try {
      first.get(5, TimeUnit.SECONDS);
      fail("Expected codec failure");
    } catch (java.util.concurrent.ExecutionException expected) {
      assertTrue(expected.getCause() instanceof IllegalStateException);
    }
    second.get(5, TimeUnit.SECONDS);
    verify(bytes, times(1)).write(any());
    assertSame(b, store.getConfiguration());
  }
}
