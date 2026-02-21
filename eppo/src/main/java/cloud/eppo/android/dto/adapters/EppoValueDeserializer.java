package cloud.eppo.android.dto.adapters;

import cloud.eppo.api.EppoValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jackson deserializer for {@link EppoValue}.
 *
 * <p>Handles deserialization of various JSON types to EppoValue, including booleans, numbers,
 * strings, and arrays of strings.
 */
public class EppoValueDeserializer extends StdDeserializer<EppoValue> {
  private static final Logger log = LoggerFactory.getLogger(EppoValueDeserializer.class);

  protected EppoValueDeserializer(Class<?> vc) {
    super(vc);
  }

  public EppoValueDeserializer() {
    this(null);
  }

  @Override
  public EppoValue deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    return deserializeNode(jp.getCodec().readTree(jp));
  }

  public EppoValue deserializeNode(JsonNode node) {
    EppoValue result;
    if (node == null || node.isNull()) {
      result = EppoValue.nullValue();
    } else if (node.isArray()) {
      List<String> stringArray = new ArrayList<>();
      for (JsonNode arrayElement : node) {
        if (arrayElement.isValueNode() && arrayElement.isTextual()) {
          stringArray.add(arrayElement.asText());
        } else {
          log.warn(
              "only Strings are supported for array-valued values; received: {}", arrayElement);
        }
      }
      result = EppoValue.valueOf(stringArray);
    } else if (node.isValueNode()) {
      if (node.isBoolean()) {
        result = EppoValue.valueOf(node.asBoolean());
      } else if (node.isNumber()) {
        result = EppoValue.valueOf(node.doubleValue());
      } else {
        result = EppoValue.valueOf(node.textValue());
      }
    } else {
      // If here, we don't know what to do; fail to null with a warning
      log.warn("Unexpected JSON for parsing a value: {}", node);
      result = EppoValue.nullValue();
    }

    return result;
  }
}
