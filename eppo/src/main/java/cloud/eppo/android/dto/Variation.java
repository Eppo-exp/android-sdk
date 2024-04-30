package cloud.eppo.android.dto;

public class Variation {

    private String key;

    private EppoValue value;

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public EppoValue getValue() {
        return value;
    }

    public void setValue(EppoValue value) {
        this.value = value;
    }
}
