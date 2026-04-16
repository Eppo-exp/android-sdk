package cloud.eppo.android.framework.storage;

import cloud.eppo.api.SerializableEppoConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
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
        // Restrict deserialization to the Eppo SDK and standard JDK types to prevent
        // gadget-chain attacks. ObjectInputFilter is available from API 26 (minSdk 26).
        // The allowlist covers the full transitive type graph of Configuration: cloud.eppo.**
        // (all SDK packages), java.util collections and their dollar-named inner classes
        // (listed before java.util.* due to first-match-wins semantics), and java.lang
        // primitives/wrappers. All other classes are rejected.
        ois.setObjectInputFilter(
            // Dollar-named inner classes must be listed before java.util.* because
            // ObjectInputFilter uses first-match-wins — java.util.* only covers top-level
            // class names (no $ or sub-packages), so inner classes like
            // Collections$UnmodifiableMap or HashMap$Node would otherwise hit the !* deny-all.
            ObjectInputFilter.Config.createFilter(
                "cloud.eppo.**"
                    + ";java.util.Arrays$*"              // Arrays.asList() instances
                    + ";java.util.Collections$*"         // unmodifiable/empty/singleton wrappers
                    + ";java.util.ImmutableCollections$*" // List.of / Set.of / Map.of (API 24+)
                    + ";java.util.HashMap$*"             // HashMap internal nodes
                    + ";java.util.LinkedHashMap$*"       // LinkedHashMap internal nodes
                    + ";java.util.*"
                    + ";java.lang.*"
                    + ";!*"));
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
