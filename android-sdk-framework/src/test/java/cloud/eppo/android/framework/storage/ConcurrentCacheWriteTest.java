package cloud.eppo.android.framework.storage;

import static org.junit.Assert.*;

import android.app.Application;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

@RunWith(RobolectricTestRunner.class)
public class ConcurrentCacheWriteTest {
  @Test
  public void separateStoresMustNotShareTemporaryFile() throws Exception {
    Application app = RuntimeEnvironment.getApplication();
    int writers = 8;
    CyclicBarrier barrier = new CyclicBarrier(writers);
    ExecutorService pool = Executors.newFixedThreadPool(writers);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < writers; i++) {
        byte[] bytes = new byte[4 * 1024 * 1024];
        Arrays.fill(bytes, (byte) (i + 1));
        BaseCacheFile file = new BaseCacheFile(app, "x");
        futures.add(
            pool.submit(
                () -> {
                  barrier.await(5, TimeUnit.SECONDS);
                  file.atomicWrite(bytes);
                  return null;
                }));
      }
      for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
      byte[] bytes =
          java.nio.file.Files.readAllBytes(new java.io.File(app.getFilesDir(), "x").toPath());
      assertEquals(4 * 1024 * 1024, bytes.length);
      assertTrue(bytes[0] >= 1 && bytes[0] <= writers);
      for (byte b : bytes) assertEquals(bytes[0], b);
    } finally {
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
      java.nio.file.Files.deleteIfExists(new java.io.File(app.getFilesDir(), "x").toPath());
    }
  }
}
