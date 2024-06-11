package cloud.eppo.android;

import static org.junit.Assert.assertEquals;

import cloud.eppo.ufc.dto.EppoValue;
import org.junit.Test;

public class EppoValueTest {
  @Test
  public void testDoubleValue() {
    EppoValue eppoValue = EppoValue.valueOf(123.4567);
    assertEquals(123.4567, eppoValue.doubleValue(), 0.0);
  }
}
