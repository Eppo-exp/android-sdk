package cloud.eppo.android.dto;

public class Variation {

    private String key;

    private EppoValue typedValue;

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public EppoValue getTypedValue() {
        return typedValue;
    }

    public void setTypedValue(EppoValue typedValue) {
        this.typedValue = typedValue;
    }
}
