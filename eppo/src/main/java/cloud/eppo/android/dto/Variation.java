package cloud.eppo.android.dto;

import com.google.gson.annotations.SerializedName;

public class Variation {
    private EppoValue typedValue;

    private ShardRange shardRange;

    public void setTypedValue(EppoValue typedValue) {
        this.typedValue = typedValue;
    }

    public EppoValue getTypedValue() {
        return typedValue;
    }

    public void setShardRange(ShardRange shardRange) {
        this.shardRange = shardRange;
    }

    public ShardRange getShardRange() {
        return shardRange;
    }
}
