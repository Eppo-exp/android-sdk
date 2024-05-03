package cloud.eppo.android.dto;

import java.util.HashMap;
import java.util.Map;

public class SubjectAttributes extends HashMap<String, EppoValue> {
    public SubjectAttributes() {
        super();
    }
    public SubjectAttributes(Map<String, EppoValue> startingAttributes) {
        super(startingAttributes);
    }

    public EppoValue put(String key, String value) {
        return super.put(key, EppoValue.valueOf(value));
    }

    public EppoValue put(String key, int value) {
        return super.put(key, EppoValue.valueOf(value));
    }

    public EppoValue put(String key, long value) {
        return super.put(key, EppoValue.valueOf(value));
    }

    public EppoValue put(String key, boolean value) {
        return super.put(key, EppoValue.valueOf(value));
    }
}