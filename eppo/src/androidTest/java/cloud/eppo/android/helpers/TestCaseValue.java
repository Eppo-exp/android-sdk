package cloud.eppo.android.helpers;

import cloud.eppo.api.EppoValue;
import java.util.List;
import org.json.JSONObject;

public class TestCaseValue extends EppoValue {
  private JSONObject jsonValue;

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

  private TestCaseValue(JSONObject jsonValue) {
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

  public static TestCaseValue valueOf(JSONObject jsonValue) {
    return new TestCaseValue(jsonValue);
  }

  public boolean isJson() {
    return this.jsonValue != null;
  }

  public JSONObject jsonValue() {
    return this.jsonValue;
  }
}
