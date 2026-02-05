package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cloud.eppo.android.util.ContextAttributesSerializer;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.EppoValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for ContextAttributesSerializer wire format.
 *
 * <p>These tests ensure that attributes are serialized correctly to match the wire format expected
 * by the precomputed flags API and to maintain consistency with other Eppo SDKs (JS, iOS).
 */
@RunWith(RobolectricTestRunner.class)
public class ContextAttributesSerializerTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testSerializeWithNullAttributes() {
    Map<String, Object> result = ContextAttributesSerializer.serialize(null);

    assertNotNull(result);
    assertTrue(result.containsKey("numericAttributes"));
    assertTrue(result.containsKey("categoricalAttributes"));
    assertTrue(((Map<?, ?>) result.get("numericAttributes")).isEmpty());
    assertTrue(((Map<?, ?>) result.get("categoricalAttributes")).isEmpty());
  }

  @Test
  public void testSerializeWithEmptyAttributes() {
    Attributes attrs = new Attributes();
    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    assertNotNull(result);
    assertTrue(((Map<?, ?>) result.get("numericAttributes")).isEmpty());
    assertTrue(((Map<?, ?>) result.get("categoricalAttributes")).isEmpty());
  }

  @Test
  public void testNumericAttributesSeparation() {
    Attributes attrs = new Attributes();
    attrs.put("age", EppoValue.valueOf(25));
    attrs.put("score", EppoValue.valueOf(99.5));
    attrs.put("country", EppoValue.valueOf("US"));

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    @SuppressWarnings("unchecked")
    Map<String, Number> numericAttrs = (Map<String, Number>) result.get("numericAttributes");
    @SuppressWarnings("unchecked")
    Map<String, Object> categoricalAttrs =
        (Map<String, Object>) result.get("categoricalAttributes");

    assertEquals(2, numericAttrs.size());
    assertEquals(25.0, numericAttrs.get("age").doubleValue(), 0.001);
    assertEquals(99.5, numericAttrs.get("score").doubleValue(), 0.001);

    assertEquals(1, categoricalAttrs.size());
    assertEquals("US", categoricalAttrs.get("country"));
  }

  @Test
  public void testBooleansSerializedAsNativeBooleans() {
    Attributes attrs = new Attributes();
    attrs.put("isPremium", EppoValue.valueOf(true));
    attrs.put("hasNotifications", EppoValue.valueOf(false));

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    @SuppressWarnings("unchecked")
    Map<String, Object> categoricalAttrs =
        (Map<String, Object>) result.get("categoricalAttributes");

    assertEquals(2, categoricalAttrs.size());

    // Verify booleans are stored as Boolean objects, not Strings
    assertTrue(categoricalAttrs.get("isPremium") instanceof Boolean);
    assertTrue(categoricalAttrs.get("hasNotifications") instanceof Boolean);

    assertEquals(true, categoricalAttrs.get("isPremium"));
    assertEquals(false, categoricalAttrs.get("hasNotifications"));
  }

  @Test
  public void testBooleansNotConvertedToStrings() {
    Attributes attrs = new Attributes();
    attrs.put("isActive", EppoValue.valueOf(true));

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    @SuppressWarnings("unchecked")
    Map<String, Object> categoricalAttrs =
        (Map<String, Object>) result.get("categoricalAttributes");

    // This is the key test: booleans should NOT be strings
    assertFalse(
        "Boolean should not be converted to String",
        categoricalAttrs.get("isActive") instanceof String);
    assertTrue(
        "Boolean should remain as Boolean", categoricalAttrs.get("isActive") instanceof Boolean);
  }

  @Test
  public void testNullValuesExcluded() {
    Attributes attrs = new Attributes();
    attrs.put("validString", EppoValue.valueOf("test"));
    attrs.put("validNumber", EppoValue.valueOf(42));
    attrs.put("nullValue", EppoValue.nullValue());

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    @SuppressWarnings("unchecked")
    Map<String, Number> numericAttrs = (Map<String, Number>) result.get("numericAttributes");
    @SuppressWarnings("unchecked")
    Map<String, Object> categoricalAttrs =
        (Map<String, Object>) result.get("categoricalAttributes");

    // Null values should be excluded
    assertEquals(1, numericAttrs.size());
    assertEquals(1, categoricalAttrs.size());
    assertFalse(numericAttrs.containsKey("nullValue"));
    assertFalse(categoricalAttrs.containsKey("nullValue"));
  }

  @Test
  public void testStringAttributesInCategorical() {
    Attributes attrs = new Attributes();
    attrs.put("country", EppoValue.valueOf("US"));
    attrs.put("language", EppoValue.valueOf("en-US"));
    attrs.put("platform", EppoValue.valueOf("android"));

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    @SuppressWarnings("unchecked")
    Map<String, Object> categoricalAttrs =
        (Map<String, Object>) result.get("categoricalAttributes");

    assertEquals(3, categoricalAttrs.size());
    assertEquals("US", categoricalAttrs.get("country"));
    assertEquals("en-US", categoricalAttrs.get("language"));
    assertEquals("android", categoricalAttrs.get("platform"));
  }

  @Test
  public void testMixedAttributeTypes() {
    Attributes attrs = new Attributes();
    // Numeric
    attrs.put("age", EppoValue.valueOf(30));
    attrs.put("lifetimeValue", EppoValue.valueOf(543.21));
    // Boolean
    attrs.put("isPremium", EppoValue.valueOf(true));
    attrs.put("hasPushEnabled", EppoValue.valueOf(false));
    // String
    attrs.put("country", EppoValue.valueOf("US"));
    attrs.put("language", EppoValue.valueOf("en"));
    // Null (should be excluded)
    attrs.put("nullAttr", EppoValue.nullValue());

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);

    @SuppressWarnings("unchecked")
    Map<String, Number> numericAttrs = (Map<String, Number>) result.get("numericAttributes");
    @SuppressWarnings("unchecked")
    Map<String, Object> categoricalAttrs =
        (Map<String, Object>) result.get("categoricalAttributes");

    // Verify numeric attributes
    assertEquals(2, numericAttrs.size());
    assertEquals(30.0, numericAttrs.get("age").doubleValue(), 0.001);
    assertEquals(543.21, numericAttrs.get("lifetimeValue").doubleValue(), 0.001);

    // Verify categorical attributes (booleans + strings, excluding null)
    assertEquals(4, categoricalAttrs.size());
    assertEquals(true, categoricalAttrs.get("isPremium"));
    assertEquals(false, categoricalAttrs.get("hasPushEnabled"));
    assertEquals("US", categoricalAttrs.get("country"));
    assertEquals("en", categoricalAttrs.get("language"));
  }

  @Test
  public void testJsonSerializationFormat() throws Exception {
    Attributes attrs = new Attributes();
    attrs.put("age", EppoValue.valueOf(25));
    attrs.put("isPremium", EppoValue.valueOf(true));
    attrs.put("country", EppoValue.valueOf("US"));

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);
    String json = objectMapper.writeValueAsString(result);

    // Verify the JSON contains native boolean (true, not "true")
    assertTrue("JSON should contain native boolean true", json.contains(":true"));
    assertFalse("JSON should not contain string \"true\"", json.contains(":\"true\""));

    // Verify the JSON contains native number
    assertTrue("JSON should contain native number", json.contains(":25"));

    // Verify the JSON contains string with quotes
    assertTrue("JSON should contain quoted string", json.contains("\"US\""));
  }

  @Test
  public void testWireFormatMatchesTestData() throws Exception {
    // This test verifies the format matches what's in sdk-test-data/precomputed-v1.json
    // The test data has:
    // "subjectAttributes": {
    //   "categoricalAttributes": { "platform": "ios", "hasPushEnabled": false },
    //   "numericAttributes": { "lastLoginDays": 3, "lifetimeValue": 543.21 }
    // }

    Attributes attrs = new Attributes();
    attrs.put("platform", EppoValue.valueOf("ios"));
    attrs.put("hasPushEnabled", EppoValue.valueOf(false));
    attrs.put("lastLoginDays", EppoValue.valueOf(3));
    attrs.put("lifetimeValue", EppoValue.valueOf(543.21));

    Map<String, Object> result = ContextAttributesSerializer.serialize(attrs);
    String json = objectMapper.writeValueAsString(result);

    // Verify structure
    assertTrue(json.contains("\"numericAttributes\""));
    assertTrue(json.contains("\"categoricalAttributes\""));

    // Verify boolean is native (false, not "false")
    assertTrue("Boolean false should be native JSON boolean", json.contains(":false"));
    assertFalse(
        "Boolean should not be string \"false\"",
        json.contains(":\"false\"") || json.contains("\"hasPushEnabled\":\"false\""));

    // Verify numeric values
    @SuppressWarnings("unchecked")
    Map<String, Number> numericAttrs = (Map<String, Number>) result.get("numericAttributes");
    assertEquals(3.0, numericAttrs.get("lastLoginDays").doubleValue(), 0.001);
    assertEquals(543.21, numericAttrs.get("lifetimeValue").doubleValue(), 0.001);
  }
}
