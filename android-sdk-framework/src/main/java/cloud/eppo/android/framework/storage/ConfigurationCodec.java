package cloud.eppo.android.framework.storage;

import cloud.eppo.api.SerializableEppoConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for serializing and deserializing configurations to and from bytes.
 *
 * <p>Used for persisting configurations to storage.
 *
 * @param <T> the configuration type, must extend SerializableEppoConfiguration
 */
public interface ConfigurationCodec<T extends SerializableEppoConfiguration> {
  /**
   * Serializes a configuration to bytes for storage.
   *
   * @param configuration the configuration to serialize
   * @return serialized bytes (must not be null)
   * @throws RuntimeException if the configuration cannot be serialized
   */
  byte[] toBytes(@NotNull T configuration);

  /**
   * Deserializes a configuration from bytes produced by {@link #toBytes}.
   *
   * @param bytes serialized configuration (must not be null)
   * @return the deserialized configuration
   * @throws RuntimeException if the bytes cannot be deserialized to a configuration
   */
  @NotNull T fromBytes(byte[] bytes);

  /**
   * Returns the MIME content type of the serialized form (e.g. {@code
   * application/x-java-serialized-object}). The codec is agnostic of storage; callers that need a
   * file extension can map this to one locally.
   */
  @NotNull String getContentType();

  /**
   * Default implementation using Java serialization.
   *
   * <p><strong>Security Note:</strong> Java serialization is used for local storage. Do not use
   * this codec to deserialize data from untrusted sources, as Java deserialization has known
   * security vulnerabilities.
   *
   * @param <T> the configuration type, must extend SerializableEppoConfiguration
   */
  public static class Default<T extends SerializableEppoConfiguration>
      implements ConfigurationCodec<T> {
    private final Class<T> configClass;

    /**
     * Creates a default codec for the specified configuration class.
     *
     * @param configClass the class of the configuration type
     */
    public Default(@NotNull Class<T> configClass) {
      if (configClass == null) {
        throw new IllegalArgumentException("configClass must not be null");
      }
      this.configClass = configClass;
    }

    @Override
    public byte[] toBytes(@NotNull T configuration) {
      if (configuration == null) {
        throw new IllegalArgumentException("Configuration must not be null");
      }
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(baos)) {
        oos.writeObject(configuration);
        return baos.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Failed to serialize configuration", e);
      }
    }

    @Override
    @SuppressWarnings("unchecked") // Safe cast - verified by configClass.isInstance() check
    public @NotNull T fromBytes(byte[] bytes) {
      if (bytes == null) {
        throw new IllegalArgumentException("Bytes must not be null");
      }
      try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
        // java.io.ObjectInputFilter (Java 9+) is not part of Android's SDK even at API 26, so
        // a pattern-based allowlist cannot be applied at compile time. The deserialization risk
        // is low here: the cache file lives in the app's private internal storage (inaccessible
        // to other apps on a non-rooted device) and the bytes are produced by the SDK's own
        // ObjectOutputStream write path — not transmitted directly from any server. The type
        // check below prevents the wrong type from being returned to callers; note that it does
        // not prevent gadget-chain execution, which occurs inside readObject() before the check.
        Object obj = ois.readObject();
        if (!configClass.isInstance(obj)) {
          throw new RuntimeException(
              "Deserialized object is not a "
                  + configClass.getSimpleName()
                  + ": "
                  + obj.getClass().getName());
        }
        return (T) obj;
      } catch (IOException e) {
        throw new RuntimeException("Failed to deserialize configuration", e);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Configuration class not found", e);
      }
    }

    @Override
    public @NotNull String getContentType() {
      return "application/x-java-serialized-object";
    }
  }
}
