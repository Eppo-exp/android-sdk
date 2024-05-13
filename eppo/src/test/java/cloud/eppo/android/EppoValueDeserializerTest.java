package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.deserializers.EppoValueDeserializer;

public class EppoValueDeserializerTest {
    private EppoValueDeserializer adapter = new EppoValueDeserializer();

    @Test
    public void testDeserializingDouble() {
        JsonElement object = JsonParser.parseString("1");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNumeric());
        assertEquals(value.doubleValue(), 1, 0.001);
    }

    @Test
    public void testDeserializingBoolean() {
        JsonElement object = JsonParser.parseString("true");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isBoolean());
        assertTrue(value.booleanValue());
    }

    @Test
    public void testDeserializingString() {
        JsonElement object = JsonParser.parseString("\"true\"");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isString());
        assertEquals(value.stringValue(), "true");
    }

    @Test
    public void testDeserializingArray() {
        JsonElement object = JsonParser.parseString("[\"value1\", \"value2\"]");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isStringArray());
        assertTrue(value.stringArrayValue().contains("value1"));
    }

    @Test
    public void testDeserializingNull() {
        JsonElement object = JsonParser.parseString("null");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNull());
    }

    @Test
    public void testDeserializingJSON() {
        JsonElement object = JsonParser.parseString("\"{\\\"a\\\": 1, \\\"b\\\": true, \\\"c\\\": \\\"hello\\\"}\"");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isJson());
        JsonElement jsonValue = value.jsonValue();
        assertNotNull(jsonValue);
        assertTrue(jsonValue.isJsonObject());
        assertEquals(1, jsonValue.getAsJsonObject().get("a").getAsInt());
        assertTrue(jsonValue.getAsJsonObject().get("b").getAsBoolean());
        assertEquals("hello", jsonValue.getAsJsonObject().get("c").getAsString());
    }

    @Test
    public void testUnexpectedObject() {
        JsonElement object = JsonParser.parseString("{\"a\": 1, \"b\": true, \"c\": \"hello\"}");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNull());
    }
}