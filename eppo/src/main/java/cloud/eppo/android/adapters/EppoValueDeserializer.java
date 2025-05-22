package cloud.eppo.android.adapters;

import androidx.annotation.Nullable;
import cloud.eppo.api.EppoValue;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class EppoValueDeserializer {
  public EppoValue deserialize(@Nullable Object valueNode) {
    if (valueNode == null || JSONObject.NULL.equals(valueNode)) {
      return EppoValue.nullValue();
    }
    if (valueNode instanceof String) {
      return EppoValue.valueOf((String) valueNode);
    }
    if (valueNode instanceof Integer || valueNode instanceof Double) {
      return EppoValue.valueOf((double) valueNode);
    }
    if (valueNode instanceof Boolean) {
      return EppoValue.valueOf((boolean) valueNode);
    }
    if (valueNode instanceof List) {
      List<String> strings = new ArrayList<>();
      for (Object item : (List<Object>) valueNode) {
        strings.add((String) item);
      }
      return EppoValue.valueOf(strings);
    }
    throw new RuntimeException("unknown value type");
  }
}
