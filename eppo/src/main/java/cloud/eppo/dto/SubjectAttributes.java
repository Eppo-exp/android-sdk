package cloud.eppo.dto;

import java.util.HashMap;

public class SubjectAttributes extends HashMap<String, EppoValue> {
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