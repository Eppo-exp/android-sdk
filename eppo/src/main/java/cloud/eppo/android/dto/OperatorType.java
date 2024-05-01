package cloud.eppo.android.dto;

public enum OperatorType {
    MATCHES("MATCHES"),
    GREATER_THAN_OR_EQUAL_TO("GTE"),
    GREATER_THAN("GT"),
    LESS_THAN_OR_EQUAL_TO("LTE"),
    LESS_THAN("LT"),
    ONE_OF("ONE_OF"),
    NOT_ONE_OF("NOT_ONE_OF"),
    IS_NULL("IS_NULL");

    public final String value;

    OperatorType(String value) {
        this.value = value;
    }

    public static OperatorType fromString(String value) {
        for (OperatorType type : OperatorType.values()) {
            // TODO: also check hashed version
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
