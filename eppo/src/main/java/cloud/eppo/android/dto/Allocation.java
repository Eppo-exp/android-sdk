package cloud.eppo.android.dto;

import java.util.List;

public class Allocation {
    private float percentExposure;

    private List<Variation> variations;

    public void setPercentExposure(float percentExposure) {
        this.percentExposure = percentExposure;
    }

    public float getPercentExposure() {
        return percentExposure;
    }

    public void setVariations(List<Variation> variations) {
        this.variations = variations;
    }

    public List<Variation> getVariations() {
        return variations;
    }
}
