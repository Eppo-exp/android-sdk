package cloud.eppo.android.dto;

import com.google.gson.annotations.SerializedName;

public class Variation {
    @SerializedName("typedValue")
    private EppoValue typedValue;
    
    @SerializedName("shardRange")
    private ShardRange shardRange;

    public EppoValue getTypedValue() {
        return typedValue;
    }

    public ShardRange getShardRange() {
        return shardRange;
    }
}
