package cloud.eppo.android;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cloud.eppo.ufc.dto.EppoValue;

public class EppoValueTest {
    @Test
    public void testDoubleValue() {
        EppoValue eppoValue = EppoValue.valueOf(123.4567);
        assertTrue(eppoValue.doubleValue() == 123.4567);
    }
}
