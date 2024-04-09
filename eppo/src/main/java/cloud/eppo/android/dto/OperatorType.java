package cloud.eppo.android.dto;

public enum OperatorType {
    Matches("MATCHES"),
    GreaterThanEqualTo("GTE"),
    GreaterThan("GT"),
    LessThanEqualTo("LTE"),
    LessThan("LT"),
    OneOf("ONE_OF"),
    NotOneOf("NOT_ONE_OF");

    public String value;

    OperatorType(String value) {
        this.value = value;
    }

    public static OperatorType fromString(String value) {
        for (OperatorType type : OperatorType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
