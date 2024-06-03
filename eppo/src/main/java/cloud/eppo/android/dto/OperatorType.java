package cloud.eppo.android.dto;

import static cloud.eppo.android.util.Utils.getMD5Hex;

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
            if (type.value.equals(value)
                || getMD5Hex(type.value).equals(value)
                || getMD5Hex(type.value).equals(value)
            ) {
                return type;
            }
        }
        return null;
    }

    public boolean isInequalityComparison() {
        return this == GREATER_THAN_OR_EQUAL_TO
                || this == GREATER_THAN
                || this == LESS_THAN_OR_EQUAL_TO
                || this == LESS_THAN;
    }

    public boolean isListComparison() {
        return this == ONE_OF || this == NOT_ONE_OF;
    }
}
