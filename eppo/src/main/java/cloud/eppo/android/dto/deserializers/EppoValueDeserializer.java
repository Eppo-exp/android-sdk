package cloud.eppo.android.dto.deserializers;

import static cloud.eppo.android.util.Utils.logTag;

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

public class EppoValueDeserializer implements JsonDeserializer<EppoValue>, JsonSerializer<EppoValue> {
    public static final String TAG = logTag(EppoValueDeserializer.class);

    @Override
    public EppoValue deserialize(JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        EppoValue result;

        if (jsonElement == null || jsonElement.isJsonNull()) {
            result = EppoValue.nullValue();
        } else if (jsonElement.isJsonArray()) {
            List<String> stringArray = new ArrayList<>();
            for (JsonElement arrayElement : jsonElement.getAsJsonArray()) {
                if (arrayElement.isJsonPrimitive() && arrayElement.getAsJsonPrimitive().isString()) {
                    stringArray.add(arrayElement.getAsJsonPrimitive().getAsString());
                } else {
                    Log.e(TAG, "only Strings are supported");
                }
            }
            result = EppoValue.valueOf(stringArray);
        } else if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
            if (jsonPrimitive.isBoolean()) {
                result = EppoValue.valueOf(jsonPrimitive.getAsBoolean());
            } else if (jsonPrimitive.isNumber()) {
                result = EppoValue.valueOf(jsonPrimitive.getAsDouble());
            } else {
                result = EppoValue.valueOf(jsonPrimitive.getAsString());
                // TODO: how to handle JSON?
            }
        } else {
            // If here, we don't know what to do; fail to null with a warning
            Log.w(TAG, "Unexpected JSON for parsing a value: "+jsonElement);
            result = EppoValue.nullValue();
        }

        return result;
    }

    @Override
    public JsonElement serialize(EppoValue src, Type typeOfSrc, JsonSerializationContext context) {
        if (src.isStringArray()) {
            JsonArray array = new JsonArray();
            for (String value : src.stringArrayValue()) {
                array.add(value);
            }
            return array;
        }

        if (src.isBoolean()) {
            return new JsonPrimitive(src.booleanValue());
        }

        if (src.isNumeric()) {
            try {
                return new JsonPrimitive(src.doubleValue());
            } catch (Exception ignored) {
            }
        }

        if (src.isNumeric()) {
            return null;
        }

        if (src.isJson()) {
            return src.jsonValue();
        }

        return new JsonPrimitive(src.stringValue());
    }
}
