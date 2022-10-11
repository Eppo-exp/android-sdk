package cloud.eppo.android.dto;

import java.util.List;

public class Allocation {
    private float percentExposure;
    private List<Variation> variations;

    public float getPercentExposure() {
        return percentExposure;
    }

    public List<Variation> getVariations() {
        return variations;
    }
}
