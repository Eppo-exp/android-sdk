package cloud.eppo.dto;

import java.util.List;

public class EppoValue {
    private String value;
    private EppoValueType type = EppoValueType.Null;
    private List<String> array;

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

    public static EppoValue valueOf(String value) {
        return new EppoValue(value, EppoValueType.String);
    }

    public static EppoValue valueOf(int value) {
        return new EppoValue(Integer.toString(value), EppoValueType.Number);
    }

    public static EppoValue valueOf(long value) {
        return new EppoValue(Long.toString(value), EppoValueType.Number);
    }

    public static EppoValue valueOf(boolean value) {
        return new EppoValue(Boolean.toString(value), EppoValueType.Boolean);
    }

    public static EppoValue valueOf(List<String> value) {
        return  new EppoValue(value);
    }

    public static EppoValue valueOf() {
        return  new EppoValue(EppoValueType.Null);
    }

    public int intValue() {
        return Integer.parseInt(value, 10);
    }

    public long longValue() {
        return Long.parseLong(value, 10);
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

    public boolean isNumeric() {
        try {
            Long.parseLong(value, 10);
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
}