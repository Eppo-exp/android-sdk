package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.Test;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;

public class EppoValueAdapterTest {
    private final EppoValueAdapter adapter = new EppoValueAdapter();

    @Test
    public void testDeserializingDouble() {
        JsonElement object = JsonParser.parseString("1");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertEquals(value.doubleValue(), 1, 0.001);
    }

    @Test
    public void testDeserializingBoolean() {
        JsonElement object = JsonParser.parseString("true");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.boolValue());
    }

    @Test
    public void testDeserializingString() {
        JsonElement object = JsonParser.parseString("\"true\"");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertEquals(value.stringValue(), "true");
    }

    @Test
    public void testDeserializingArray() {
        JsonElement object = JsonParser.parseString("[\"value1\", \"value2\"]");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.arrayValue().contains("value1"));
    }

    @Test
    public void testDeserializingNull() {
        JsonElement object = JsonParser.parseString("null");
        EppoValue value = adapter.deserialize(object, EppoValue.class, null);
        assertTrue(value.isNull());
    }
}