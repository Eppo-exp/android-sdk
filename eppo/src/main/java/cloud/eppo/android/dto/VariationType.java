package cloud.eppo.android.dto;

public enum VariationType {
    Boolean("BOOLEAN"),
    Integer("INTEGER"),
    Numeric("NUMERIC"),
    String("STRING"),
    JSON("JSON");

    public String value;

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
