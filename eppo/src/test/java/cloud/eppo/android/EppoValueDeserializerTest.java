package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.deserializers.EppoValueDeserializer;

public class EppoValueDeserializerTest {
    private EppoValueDeserializer adapter = new EppoValueDeserializer();

    @Test
    public void testDeserializingDouble() throws Exception {
        JsonElement object = JsonParser.parseString("1");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertEquals(value.doubleValue(), 1, 0.001);
    }

    @Test
    public void testDeserializingBoolean() throws Exception {
        JsonElement object = JsonParser.parseString("true");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertEquals(value.booleanValue(), true);
    }

    @Test
    public void testDeserializingString() throws Exception {
        JsonElement object = JsonParser.parseString("\"true\"");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertEquals(value.stringValue(), "true");
    }

    @Test
    public void testDeserializingArray() throws Exception {
        JsonElement object = JsonParser.parseString("[\"value1\", \"value2\"]");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.stringArrayValue().contains("value1"));
    }

    @Test
    public void testDeserializingNull() throws Exception {
        JsonElement object = JsonParser.parseString("null");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNull());
    }
}