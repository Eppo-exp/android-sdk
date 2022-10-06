package cloud.eppo.android;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cloud.eppo.android.dto.ShardRange;
import cloud.eppo.android.util.Utils;

public class ShardTest {
    ShardRange createShardRange(int start, int end) {
        ShardRange range = new ShardRange();
        range.setStart(start);
        range.setEnd(end);
        return range;
    }

    @Test
    public void testIsShardInRangePositiveCase() {
        ShardRange range = createShardRange(10, 20);
        assertTrue(Utils.isShardInRange(15, range));
    }

    @Test
    public void testIsShardInRangeNegativeCase() {
        ShardRange range = createShardRange(10, 20);
        assertTrue(Utils.isShardInRange(15, range));
    }

    @Test
    public void testGetShard() throws Exception {
        final int MAX_SHARD_VALUE = 200;
        int shardValue = Utils.getShard("test-user", MAX_SHARD_VALUE);
        assertTrue(shardValue >= 0 & shardValue <= MAX_SHARD_VALUE);
    }
}
