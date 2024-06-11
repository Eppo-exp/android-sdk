package cloud.eppo.android.helpers;

import cloud.eppo.ufc.dto.EppoValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public class TestCaseValue extends EppoValue {
  private JsonNode jsonValue;

  private TestCaseValue() {
    super();
  }

  private TestCaseValue(boolean boolValue) {
    super(boolValue);
  }

  private TestCaseValue(double doubleValue) {
    super(doubleValue);
  }

  private TestCaseValue(String stringValue) {
    super(stringValue);
  }

  private TestCaseValue(List<String> stringArrayValue) {
    super(stringArrayValue);
  }

  private TestCaseValue(JsonNode jsonValue) {
    super(jsonValue.toString());
    this.jsonValue = jsonValue;
  }

  public static TestCaseValue copyOf(EppoValue eppoValue) {
    if (eppoValue.isNull()) {
      return new TestCaseValue();
    } else if (eppoValue.isBoolean()) {
      return new TestCaseValue(eppoValue.booleanValue());
    } else if (eppoValue.isNumeric()) {
      return new TestCaseValue(eppoValue.doubleValue());
    } else if (eppoValue.isString()) {
      return new TestCaseValue(eppoValue.stringValue());
    } else if (eppoValue.isStringArray()) {
      return new TestCaseValue(eppoValue.stringArrayValue());
    } else {
      throw new IllegalArgumentException("Unable to copy EppoValue: " + eppoValue);
    }
  }

  public static TestCaseValue valueOf(JsonNode jsonValue) {
    return new TestCaseValue(jsonValue);
  }

  public boolean isJson() {
    return this.jsonValue != null;
  }

  public JsonNode jsonValue() {
    return this.jsonValue;
  }
}
