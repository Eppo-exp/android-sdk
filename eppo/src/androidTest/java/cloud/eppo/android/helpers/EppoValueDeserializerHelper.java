package cloud.eppo.android.helpers;

import cloud.eppo.api.EppoValue;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Helper class for deserializing JsonNode to EppoValue in tests.
 *
 * <p>This replaces the use of cloud.eppo.ufc.dto.adapters.EppoValueDeserializer which was removed
 * in v4.
 */
public class EppoValueDeserializerHelper {

  public EppoValue deserializeNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return EppoValue.nullValue();
    }
    if (node.isTextual()) {
      return EppoValue.valueOf(node.asText());
    }
    if (node.isBoolean()) {
      return EppoValue.valueOf(node.asBoolean());
    }
    if (node.isInt() || node.isLong()) {
      return EppoValue.valueOf(node.asLong());
    }
    if (node.isFloat() || node.isDouble() || node.isNumber()) {
      return EppoValue.valueOf(node.asDouble());
    }
    if (node.isArray()) {
      // For arrays, convert to JSON string representation
      return EppoValue.valueOf(node.toString());
    }
    // For objects, return as JSON string
    return EppoValue.valueOf(node.toString());
  }
}
