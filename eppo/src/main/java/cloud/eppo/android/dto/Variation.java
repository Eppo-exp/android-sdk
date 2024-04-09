package cloud.eppo.android.dto;

public class Variation {
    private EppoValue typedValue;

    private ShardRange shardRange;

    public EppoValue getTypedValue() {
        return typedValue;
    }

    public void setTypedValue(EppoValue typedValue) {
        this.typedValue = typedValue;
    }

    public ShardRange getShardRange() {
        return shardRange;
    }

    public void setShardRange(ShardRange shardRange) {
        this.shardRange = shardRange;
    }
}
