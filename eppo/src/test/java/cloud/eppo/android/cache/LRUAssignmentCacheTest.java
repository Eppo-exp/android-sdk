package cloud.eppo.android.cache;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cloud.eppo.cache.AssignmentCacheEntry;
import cloud.eppo.cache.AssignmentCacheKey;
import cloud.eppo.cache.VariationCacheValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LRUAssignmentCacheTest {
  @Test
  public void unchangedAssignmentIsDeduplicated() {
    LRUAssignmentCache cache = new LRUAssignmentCache(100);
    assertTrue(cache.putIfAbsent(entry("subject", "flag", "allocation", "A")));
    assertFalse(cache.putIfAbsent(entry("subject", "flag", "allocation", "A")));
  }

  @Test
  public void changedVariationReplacesPreviousAssignment() {
    LRUAssignmentCache cache = new LRUAssignmentCache(100);
    AssignmentCacheEntry first = entry("subject", "flag", "allocation", "A");
    AssignmentCacheEntry second = entry("subject", "flag", "allocation", "B");
    assertTrue(cache.putIfAbsent(first));
    assertTrue(cache.putIfAbsent(second));
    assertTrue(cache.hasEntry(second));
    assertFalse(cache.hasEntry(first));
    assertFalse(cache.putIfAbsent(second));
    assertTrue(cache.putIfAbsent(first));
    assertFalse(cache.putIfAbsent(first));
  }

  @Test
  public void changedAllocationIsLoggedWithTheSameVariation() {
    LRUAssignmentCache cache = new LRUAssignmentCache(100);
    assertTrue(cache.putIfAbsent(entry("subject", "flag", "allocation-1", "A")));
    assertTrue(cache.putIfAbsent(entry("subject", "flag", "allocation-2", "A")));
    assertFalse(cache.putIfAbsent(entry("subject", "flag", "allocation-2", "A")));
  }

  @Test
  public void subjectsAndFlagsHaveIndependentEntries() {
    LRUAssignmentCache cache = new LRUAssignmentCache(100);
    assertTrue(cache.putIfAbsent(entry("subject-1", "flag-1", "allocation", "A")));
    assertTrue(cache.putIfAbsent(entry("subject-2", "flag-1", "allocation", "A")));
    assertTrue(cache.putIfAbsent(entry("subject-1", "flag-2", "allocation", "A")));
    assertFalse(cache.putIfAbsent(entry("subject-1", "flag-1", "allocation", "A")));
  }

  @Test
  public void evictedAssignmentIsLoggedAgain() {
    LRUAssignmentCache cache = new LRUAssignmentCache(1);
    AssignmentCacheEntry first = entry("subject", "flag-1", "allocation", "A");
    assertTrue(cache.putIfAbsent(first));
    assertTrue(cache.putIfAbsent(entry("subject", "flag-2", "allocation", "A")));
    assertFalse(cache.hasEntry(first));
    assertTrue(cache.putIfAbsent(first));
  }

  private static AssignmentCacheEntry entry(
      String subject, String flag, String allocation, String variation) {
    return new AssignmentCacheEntry(
        new AssignmentCacheKey(subject, flag), new VariationCacheValue(allocation, variation));
  }
}
