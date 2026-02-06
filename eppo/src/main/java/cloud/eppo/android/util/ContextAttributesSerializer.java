package cloud.eppo.android.util;

import cloud.eppo.api.Attributes;
import cloud.eppo.api.EppoValue;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for serializing subject and action attributes to the wire format expected by the
 * precomputed flags API.
 *
 * <p>The API expects attributes to be separated into:
 *
 * <ul>
 *   <li>numericAttributes: numbers (integers and doubles)
 *   <li>categoricalAttributes: strings, booleans, and other non-numeric types
 * </ul>
 *
 * <p>This matches the behavior of the JS SDK and ensures consistent wire format across all Eppo
 * SDKs.
 */
public final class ContextAttributesSerializer {

  private ContextAttributesSerializer() {
    // Prevent instantiation
  }

  /**
   * Serializes attributes into the context attributes format expected by the API.
   *
   * @param attributes The attributes to serialize (can be null)
   * @return A map containing "numericAttributes" and "categoricalAttributes" keys
   */
  public static Map<String, Object> serialize(Attributes attributes) {
    Map<String, Object> result = new HashMap<>();
    Map<String, Number> numericAttrs = new HashMap<>();
    Map<String, Object> categoricalAttrs = new HashMap<>();

    if (attributes != null) {
      for (String key : attributes.keySet()) {
        EppoValue value = attributes.get(key);
        if (value != null && !value.isNull()) {
          if (value.isNumeric()) {
            numericAttrs.put(key, value.doubleValue());
          } else if (value.isBoolean()) {
            // Booleans should be serialized as native JSON booleans, not strings
            categoricalAttrs.put(key, value.booleanValue());
          } else {
            categoricalAttrs.put(key, value.stringValue());
          }
        }
      }
    }

    result.put("numericAttributes", numericAttrs);
    result.put("categoricalAttributes", categoricalAttrs);
    return result;
  }
}
