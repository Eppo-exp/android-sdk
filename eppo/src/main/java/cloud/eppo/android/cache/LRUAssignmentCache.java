package cloud.eppo.android.cache;

import android.util.LruCache;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.cache.AssignmentCacheEntry;

public class LRUAssignmentCache implements IAssignmentCache {

  public LRUAssignmentCache(int maxCacheSize) {
    cache = new LruCache<>(maxCacheSize);
  }

  private final LruCache<String, AssignmentCacheEntry> cache;

  @Override
  public void put(AssignmentCacheEntry entry) {
    cache.put(entry.getKeyString(), entry);
  }

  @Override
  public boolean hasEntry(AssignmentCacheEntry entry) {
    AssignmentCacheEntry cached = cache.get(entry.getKeyString());
    return (cached != null && cached.getValueKeyString().equals(entry.getValueKeyString()));
  }
}
