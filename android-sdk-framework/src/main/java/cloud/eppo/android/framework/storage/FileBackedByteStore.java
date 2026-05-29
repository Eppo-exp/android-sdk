package cloud.eppo.android.framework.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ByteStore} implementation that reads and writes a single file via {@link BaseCacheFile}.
 *
 * <p>Implements {@link java.io.Closeable}. Call {@link #close()} when the store is no longer needed
 * to release the background IO thread. If not closed explicitly, the daemon thread will be
 * reclaimed by the JVM/Android runtime on process exit.
 */
public final class FileBackedByteStore implements ByteStore, java.io.Closeable {

  // Dedicated single-thread executor avoids saturating ForkJoinPool.commonPool() with blocking I/O
  // on low-core-count Android devices. Instance-level (not static) so it can be shut down via
  // close(). The thread is a daemon so it does not block JVM/process exit when close() is omitted
  // (e.g. in test environments such as Robolectric).
  private final ExecutorService ioExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "eppo-io");
            t.setDaemon(true);
            return t;
          });

  private final BaseCacheFile cacheFile;

  public FileBackedByteStore(@NotNull BaseCacheFile cacheFile) {
    if (cacheFile == null) {
      throw new IllegalArgumentException("cacheFile must not be null");
    }
    this.cacheFile = cacheFile;
  }

  /**
   * Shuts down the background IO executor. In-flight operations are allowed to complete; no new
   * operations will be accepted after this call.
   */
  @Override
  public void close() {
    ioExecutor.shutdown();
  }

  @Override
  @NotNull public CompletableFuture<byte[]> read() {
    return CompletableFuture.supplyAsync(
        () -> {
          if (!cacheFile.exists()) {
            return null;
          }
          try (java.io.InputStream in = cacheFile.getInputStream()) {
            return readAllBytes(in);
          } catch (Exception e) {
            throw new RuntimeException("Failed to read from cache file", e);
          }
        },
        ioExecutor);
  }

  @Override
  @NotNull public CompletableFuture<Void> write(@NotNull byte[] bytes) {
    if (bytes == null) {
      throw new IllegalArgumentException("bytes must not be null");
    }
    return CompletableFuture.runAsync(
        () -> {
          try (java.io.OutputStream out = cacheFile.getOutputStream()) {
            out.write(bytes);
          } catch (Exception e) {
            throw new RuntimeException("Failed to write to cache file", e);
          }
        },
        ioExecutor);
  }

  private static byte[] readAllBytes(java.io.InputStream in) throws java.io.IOException {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      baos.write(buf, 0, n);
    }
    return baos.toByteArray();
  }
}
