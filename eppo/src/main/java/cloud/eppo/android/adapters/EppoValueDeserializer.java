package cloud.eppo.android.adapters;

import androidx.annotation.Nullable;
import cloud.eppo.api.EppoValue;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class EppoValueDeserializer {
  private final boolean isObfuscated;

  public EppoValueDeserializer(boolean isObfuscated) {
    this.isObfuscated = isObfuscated;
  }

  public EppoValueDeserializer() {
    this(false);
  }

  public EppoValue deserialize(@Nullable Object valueNode) {
    if (valueNode == null || JSONObject.NULL.equals(valueNode)) {
      return EppoValue.nullValue();
    }
    if (valueNode instanceof String) {
      return EppoValue.valueOf((String) valueNode);
    }
    if (valueNode instanceof Integer || valueNode instanceof Double) {
      return EppoValue.valueOf(Double.parseDouble(valueNode.toString()));
    }
    if (valueNode instanceof Boolean) {
      return EppoValue.valueOf((boolean) valueNode);
    }
    if (valueNode instanceof JSONArray) {
      List<String> strings = new ArrayList<>();
      JSONArray jArray = (JSONArray) valueNode;
      for (int i = 0; i < jArray.length(); i++) {
        strings.add(jArray.optString(i));
      }
      return EppoValue.valueOf(strings);
    }
    throw new RuntimeException("unknown value type");
  }
}
