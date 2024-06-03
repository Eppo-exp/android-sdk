package cloud.eppo.android.dto.adapters;

import static cloud.eppo.android.util.Utils.getISODate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Date;

/**
 * This adapter for Date allows gson to serialize to UTC ISO 8601 (vs. its default of local timezone)
 */
public class DateAdapter implements JsonSerializer<Date> {

    @Override
    public JsonElement serialize(Date src, Type typeOfT, JsonSerializationContext context) {
        return new JsonPrimitive(getISODate(src));
    }
}
