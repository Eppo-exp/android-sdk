package cloud.eppo.android.dto;

import com.google.gson.JsonElement;

import java.util.List;

public class EppoValue {
    private String value;
    private EppoValueType type = EppoValueType.Null;
    private List<String> array;

    private JsonElement json;

    // TODO: come up with better name?!
    public EppoValue() {}

    public EppoValue(String value, EppoValueType type) {
        this.value = value;
        this.type = type;
    }

    public EppoValue(List<String> array) {
        this.array = array;
        this.type = EppoValueType.ArrayOfStrings;
    }

    public EppoValue(EppoValueType type) {
        this.type = type;
    }

    public EppoValue(JsonElement json) {
        this.json = json;
        this.value = json.toString();
        this.type = EppoValueType.JSON;
    }

    public static EppoValue valueOf(String value) {
        return new EppoValue(value, EppoValueType.String);
    }

    public static EppoValue valueOf(double value) {
        return new EppoValue(Double.toString(value), EppoValueType.Number);
    }

    public static EppoValue valueOf(boolean value) {
        return new EppoValue(Boolean.toString(value), EppoValueType.Boolean);
    }

    public static EppoValue valueOf(List<String> value) {
        return  new EppoValue(value);
    }

    public static EppoValue valueOf(JsonElement json) {
        return new EppoValue(json);
    }

    public static EppoValue valueOf() {
        return  new EppoValue(EppoValueType.Null);
    }

    public double doubleValue() {
        return Double.parseDouble(value);
    }

    public String stringValue() {
        return value;
    }

    public boolean boolValue() {
        return Boolean.valueOf(value);
    }

    public List<String> arrayValue() {
        return  array;
    }

    public JsonElement jsonValue() {
        return this.json;
    }

    public boolean isNumeric() {
        try {
            Double.parseDouble(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isArray() {
        return type == EppoValueType.ArrayOfStrings;
    }

    public boolean isBool() {
        return  type == EppoValueType.Boolean;
    }

    public boolean isNull() {
        return type == EppoValueType.Null;
    }

    public boolean isJSON() {
        return type == EppoValueType.JSON;
    }

    @Override
    public String toString() {
        return "EppoValue{" +
                "value='" + value + '\'' +
                ", type=" + type +
                ", array=" + array +
                '}';
    }
}