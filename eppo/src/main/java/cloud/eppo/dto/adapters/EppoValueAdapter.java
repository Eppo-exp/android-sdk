package cloud.eppo.dto.adapters;

import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import cloud.eppo.dto.EppoValue;

public class EppoValueAdapter implements JsonDeserializer<EppoValue> {
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
                return EppoValue.valueOf(json.getAsString());
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
}
