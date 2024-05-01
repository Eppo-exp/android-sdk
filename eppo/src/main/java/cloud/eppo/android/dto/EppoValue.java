package cloud.eppo.android.dto;

import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.List;

public class EppoValue {
    private final EppoValueType type;
    private Boolean boolValue;
    private Double doubleValue;
    private String stringValue;
    private List<String> stringArrayValue;
    private JsonElement jsonValue;

    private EppoValue() {
        this.type = EppoValueType.NULL;
    }

    private EppoValue(boolean boolValue) {
        this.boolValue = boolValue;
        this.type = EppoValueType.BOOLEAN;
    }

    private EppoValue(double doubleValue) {
        this.doubleValue = doubleValue;
        this.type = EppoValueType.NUMBER;
    }

    private EppoValue(String stringValue) {
        this.stringValue = stringValue;
        this.type = EppoValueType.STRING;
    }

    private EppoValue(List<String> stringArrayValue) {
        this.stringArrayValue = stringArrayValue;
        this.type = EppoValueType.ARRAY_OF_STRING;
    }

    private EppoValue(JsonElement jsonValue) {
        this.jsonValue = jsonValue;
        this.type = EppoValueType.JSON;
    }

    public static EppoValue nullValue() {
        return new EppoValue();
    }

    public static EppoValue valueOf(boolean boolValue) {
        return new EppoValue(boolValue);
    }

    public static EppoValue valueOf(double doubleValue) {
        return new EppoValue(doubleValue);
    }

    public static EppoValue valueOf(String stringValue) {
        return new EppoValue(stringValue);
    }

    public static EppoValue valueOf(List<String> value) {
        return new EppoValue(value);
    }

    public static EppoValue valueOf(JsonElement jsonValue) {
        return new EppoValue(jsonValue);
    }

    public boolean booleanValue() {
        return this.boolValue;
    }

    public double doubleValue() {
        return this.doubleValue;
    }

    public String stringValue() {
        return this.stringValue;
    }

    public List<String> stringArrayValue() {
        return this.stringArrayValue;
    }

    public JsonElement jsonValue() {
        return this.jsonValue;
    }

    public boolean isNull() {
        return type == EppoValueType.NULL;
    }

    public boolean isBoolean() {
        return this.type == EppoValueType.BOOLEAN;
    }

    public boolean isNumeric() {
        return this.type == EppoValueType.NUMBER;
    }

    public boolean isString() {
        return this.type == EppoValueType.STRING;
    }

    public boolean isStringArray() {
        return type == EppoValueType.ARRAY_OF_STRING;
    }

    public boolean isJson() {
        return type == EppoValueType.JSON;
    }

    @Override
    public String toString() {
        switch(this.type) {
            case STRING:
                return this.stringValue;
            case NUMBER:
                return this.doubleValue.toString();
            case BOOLEAN:
                return this.boolValue.toString();
            case ARRAY_OF_STRING:
                return Collections.singletonList(this.stringArrayValue).toString();
            case JSON:
                return this.jsonValue.toString();
            default: // NULL
                return "";
        }
    }
}