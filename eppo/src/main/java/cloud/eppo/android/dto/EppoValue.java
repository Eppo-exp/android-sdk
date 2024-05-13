package cloud.eppo.android.dto;

import androidx.annotation.NonNull;

import java.util.List;

public class EppoValue {
    protected final EppoValueType type;
    protected Boolean boolValue;
    protected Double doubleValue;
    protected String stringValue;
    protected List<String> stringArrayValue;

    protected EppoValue() {
        this.type = EppoValueType.NULL;
    }

    protected EppoValue(boolean boolValue) {
        this.boolValue = boolValue;
        this.type = EppoValueType.BOOLEAN;
    }

    protected EppoValue(double doubleValue) {
        this.doubleValue = doubleValue;
        this.type = EppoValueType.NUMBER;
    }

    protected EppoValue(String stringValue) {
        this.stringValue = stringValue;
        this.type = EppoValueType.STRING;
    }

    protected EppoValue(List<String> stringArrayValue) {
        this.stringArrayValue = stringArrayValue;
        this.type = EppoValueType.ARRAY_OF_STRING;
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

    @NonNull
    @Override
    public String toString() {
        switch(this.type) {
            case BOOLEAN:
                return this.boolValue.toString();
            case NUMBER:
                return this.doubleValue.toString();
            case STRING:
                return this.stringValue;
            case ARRAY_OF_STRING:
                return String.join(" ,", this.stringArrayValue);
            default: // NULL
                return "";
        }
    }
}