package cloud.eppo.android.framework.storage;

import cloud.eppo.api.Configuration;
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
 * @param <ConfigurationType> the configuration type, must extend SerializableEppoConfiguration
 */
public interface ConfigurationCodec<ConfigurationType extends SerializableEppoConfiguration> {
  /**
   * Serializes a configuration to bytes for storage.
   *
   * @param configuration the configuration to serialize
   * @return serialized bytes (must not be null)
   * @throws RuntimeException if the configuration cannot be serialized
   */
  byte[] toBytes(@NotNull ConfigurationType configuration);

  /**
   * Deserializes a configuration from bytes produced by {@link #toBytes}.
   *
   * @param bytes serialized configuration (must not be null)
   * @return the deserialized configuration
   * @throws RuntimeException if the bytes cannot be deserialized to a configuration
   */
  @NotNull ConfigurationType fromBytes(byte[] bytes);

  /**
   * Returns the MIME content type of the serialized form (e.g. {@code
   * application/x-java-serialized-object}). The codec is agnostic of storage; callers that need a
   * file extension can map this to one locally.
   */
  @NotNull String getContentType();

  /**
   * Generic equivalent to {@link Configuration#emptyConfig()}
   *
   * @return an empty Configuration.
   */
  @NotNull ConfigurationType emptyConfiguration();

  /**
   * Default implementation using Java serialization.
   *
   * <p><strong>Security Note:</strong> Java serialization is used for local storage. Do not use
   * this codec to deserialize data from untrusted sources, as Java deserialization has known
   * security vulnerabilities.
   *
   * @param <ConfigurationType> the configuration type, must extend SerializableEppoConfiguration
   */
  public static class Default implements ConfigurationCodec<Configuration> {
    @Override
    public byte[] toBytes(@NotNull Configuration configuration) {
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
    public @NotNull Configuration fromBytes(byte[] bytes) {
      if (bytes == null) {
        throw new IllegalArgumentException("Bytes must not be null");
      }
      try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
        Object obj = ois.readObject();
        if (!(obj instanceof Configuration)) {
          throw new RuntimeException(
              "Deserialized object is not a Configuration:" + obj.getClass().getName());
        }
        return (Configuration) obj;
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

    @Override
    public @NotNull Configuration emptyConfiguration() {
      return Configuration.emptyConfig();
    }
  }
}
