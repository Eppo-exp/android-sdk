package cloud.eppo.android.cache;

import android.util.LruCache;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.logging.Assignment;

public class LRUAssignmentCache implements IAssignmentCache {
  private final LruCache<String, Assignment> cache;

  public LRUAssignmentCache(int maxCacheSize) {
    cache = new LruCache<>(maxCacheSize);
  }

  @Override
  public void put(@NonNull String key, @NonNull Assignment assignment) {
    cache.put(key, assignment);
  }

  @Override
  @Nullable public Assignment get(@NonNull String key) {
    return cache.get(key);
  }

  @Override
  public boolean containsKey(@NonNull String key) {
    return get(key) != null;
  }

  @Override
  public void remove(@NonNull String key) {
    cache.remove(key);
  }
}
