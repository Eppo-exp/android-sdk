package cloud.eppo.android.api;

import androidx.annotation.NonNull;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;

/**
 * Interface for parsing precomputed configuration responses.
 *
 * <p>This interface allows framework consumers to provide their own JSON parsing implementation.
 * The batteries-included artifact provides a Jackson-based implementation.
 *
 * @param <JSONFlagType> The JSON tree type (e.g., JsonNode for Jackson)
 */
public interface PrecomputedConfigParser<JSONFlagType> {

  /**
   * Parses the raw precomputed configuration response bytes.
   *
   * @param responseBytes Raw JSON bytes from the server
   * @return Parsed precomputed configuration
   * @throws PrecomputedParseException if parsing fails
   */
  @NonNull PrecomputedConfigurationResponse parse(@NonNull byte[] responseBytes)
      throws PrecomputedParseException;

  /**
   * Parses a Base64-encoded JSON value to the appropriate type. Used for JSON flag values in
   * precomputed responses.
   *
   * @param base64EncodedValue Base64-encoded JSON string
   * @return Parsed JSON value of the generic type
   * @throws PrecomputedParseException if parsing fails
   */
  @NonNull JSONFlagType parseJsonValue(@NonNull String base64EncodedValue) throws PrecomputedParseException;
}
