package cloud.eppo.android.dto;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Allocation {
    @SerializedName("percentExposure")
    private float percentExposure;

    @SerializedName("variations")
    private List<Variation> variations;

    public float getPercentExposure() {
        return percentExposure;
    }

    public List<Variation> getVariations() {
        return variations;
    }
}
