package cloud.eppo.android.framework.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ByteStore} implementation that reads and writes a single file via {@link BaseCacheFile}.
 */
public final class FileBackedByteStore implements ByteStore {

  // Dedicated single-thread executor avoids saturating ForkJoinPool.commonPool() with blocking I/O
  // on low-core-count Android devices.
  private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

  private final BaseCacheFile cacheFile;

  public FileBackedByteStore(@NotNull BaseCacheFile cacheFile) {
    if (cacheFile == null) {
      throw new IllegalArgumentException("cacheFile must not be null");
    }
    this.cacheFile = cacheFile;
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
        IO_EXECUTOR);
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
        IO_EXECUTOR);
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
