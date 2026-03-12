package cloud.eppo.android.dto.adapters;

import static cloud.eppo.Utils.getISODate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Date;

/**
 * This adapter for Date allows gson to serialize to UTC ISO 8601 (vs. its default of local
 * timezone)
 */
public class DateSerializer extends StdSerializer<Date> {
  protected DateSerializer(Class<Date> t) {
    super(t);
  }

  public DateSerializer() {
    this(null);
  }

  @Override
  public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider)
      throws IOException {
    jgen.writeString(getISODate(value));
  }
}
