package cloud.eppo.dto;

import java.util.List;

public class Allocation {
    private int percentExposure;
    private List<Variation> variations;

    public int getPercentExposure() {
        return percentExposure;
    }

    public List<Variation> getVariations() {
        return variations;
    }
}
