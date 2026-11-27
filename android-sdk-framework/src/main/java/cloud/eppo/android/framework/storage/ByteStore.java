package cloud.eppo.android.framework.storage;

import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/**
 * Abstraction for asynchronous byte-level I/O operations.
 *
 * <p>Implementations handle reading and writing raw bytes to/from persistent storage. This
 * interface is agnostic of serialization format and storage medium.
 */
public interface ByteStore {

  /**
   * Reads bytes from storage asynchronously.
   *
   * @return a CompletableFuture that completes with the read bytes, or null if the storage does not
   *     exist
   * @throws RuntimeException (via CompletableFuture) if an I/O error occurs during read
   */
  @NotNull CompletableFuture<byte[]> read();

  /**
   * Writes bytes to storage asynchronously.
   *
   * @param bytes the bytes to write (must not be null)
   * @return a CompletableFuture that completes when the write operation finishes
   * @throws IllegalArgumentException if bytes is null
   * @throws RuntimeException (via CompletableFuture) if an I/O error occurs during write
   */
  @NotNull CompletableFuture<Void> write(@NotNull byte[] bytes);
}
