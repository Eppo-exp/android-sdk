package cloud.eppo.android.dto;

public enum VariationType {
    BOOLEAN("BOOLEAN"),
    INTEGER("INTEGER"),
    NUMERIC("NUMERIC"),
    STRING("STRING"),
    JSON("JSON");

    public final String value;

    VariationType(String value) {
        this.value = value;
    }

    public static VariationType fromString(String value) {
        for (VariationType type : VariationType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
