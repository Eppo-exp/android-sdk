package cloud.eppo.android.dto.adapters;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import cloud.eppo.android.dto.EppoValue;

public class EppoValueAdapter implements JsonDeserializer<EppoValue>, JsonSerializer<EppoValue> {
    @Override
    public EppoValue deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonArray()) {
            List<String> array = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                try {
                    array.add(element.getAsString());
                } catch (Exception e) {
                    Log.e(EppoValueAdapter.class.getCanonicalName(), "only Strings are supported");
                }
            }
            return EppoValue.valueOf(array);
        }

        if (json.isJsonPrimitive()) {
            try {
                return EppoValue.valueOf(json.getAsInt());
            } catch (Exception ignored) {}

            try {
                String stringValue = json.getAsString();
                if (stringValue == "null") {
                    return EppoValue.valueOf();
                }
                return EppoValue.valueOf(stringValue);
            } catch (Exception ignored) {}

            try {
                return EppoValue.valueOf(json.getAsLong());
            } catch (Exception ignored) {}

            try {
                return EppoValue.valueOf(json.getAsBoolean());
            } catch (Exception ignored) {}
        }

        return EppoValue.valueOf();
    }

    @Override
    public JsonElement serialize(EppoValue src, Type typeOfSrc, JsonSerializationContext context) {
        if (src.isArray()) {
            JsonArray array = new JsonArray();
            for (String value : src.arrayValue()) {
                array.add(value);
            }
            return array;
        }

        if (src.isBool()) {
            return new JsonPrimitive(src.boolValue());
        }

        if (src.isNumeric()) {
            try {
                return new JsonPrimitive(src.doubleValue());
            } catch (Exception ignored) {}
        }

        if (src.isNumeric()) {
            return null;
        }

        return new JsonPrimitive(src.stringValue());
    }
}
