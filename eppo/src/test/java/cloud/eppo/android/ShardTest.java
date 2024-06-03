package cloud.eppo.android;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cloud.eppo.android.dto.Range;
import cloud.eppo.android.util.Utils;

public class ShardTest {
    Range createRange(int start, int end) {
        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);
        return range;
    }

    @Test
    public void testIsShardInRangePositiveCase() {
        Range range = createRange(10, 20);
        assertTrue(Utils.isShardInRange(15, range));
    }

    @Test
    public void testIsShardInRangeNegativeCase() {
        Range range = createRange(10, 20);
        assertTrue(Utils.isShardInRange(15, range));
    }

    @Test
    public void testGetShard() throws Exception {
        final int MAX_SHARD_VALUE = 200;
        int shardValue = Utils.getShard("test-user", MAX_SHARD_VALUE);
        assertTrue(shardValue >= 0 & shardValue <= MAX_SHARD_VALUE);
    }
}
