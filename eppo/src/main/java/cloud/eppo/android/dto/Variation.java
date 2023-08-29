package cloud.eppo.android.dto;

public class Variation {
    private EppoValue typedValue;
    private ShardRange shardRange;

    public EppoValue getTypedValue() {
        return typedValue;
    }

    public ShardRange getShardRange() {
        return shardRange;
    }
}
