package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

import cloud.eppo.ufc.dto.EppoValue;
import cloud.eppo.ufc.dto.adapters.EppoValueAdapter;

public class EppoValueAdapterTest {
    private final EppoValueAdapter deserializer = new EppoValueAdapter();

    @Test
    public void testDeserializingDouble() {
        JsonElement object = JsonParser.parseString("1");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNumeric());
        assertEquals(value.doubleValue(), 1, 0.001);
    }

    @Test
    public void testDeserializingBoolean() {
        JsonElement object = JsonParser.parseString("true");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isBoolean());
        assertTrue(value.booleanValue());
    }

    @Test
    public void testDeserializingString() {
        JsonElement object = JsonParser.parseString("\"true\"");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isString());
        assertEquals(value.stringValue(), "true");
    }

    @Test
    public void testDeserializingArray() {
        JsonElement object = JsonParser.parseString("[\"value1\", \"value2\"]");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isStringArray());
        assertTrue(value.stringArrayValue().contains("value1"));
    }

    @Test
    public void testDeserializingNull() {
        JsonElement object = JsonParser.parseString("null");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNull());
    }

    @Test
    public void testDeserializingJSON() {
        JsonElement object = JsonParser.parseString("\"{\\\"a\\\": 1, \\\"b\\\": true, \\\"c\\\": \\\"hello\\\"}\"");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isString());
        JsonElement jsonValue = JsonParser.parseString(value.stringValue());
        assertNotNull(jsonValue);
        assertTrue(jsonValue.isJsonObject());
        assertEquals(1, jsonValue.getAsJsonObject().get("a").getAsInt());
        assertTrue(jsonValue.getAsJsonObject().get("b").getAsBoolean());
        assertEquals("hello", jsonValue.getAsJsonObject().get("c").getAsString());
    }

    @Test
    public void testUnexpectedObject() {
        JsonElement object = JsonParser.parseString("{\"a\": 1, \"b\": true, \"c\": \"hello\"}");
        EppoValue value = deserializer.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNull());
    }
}