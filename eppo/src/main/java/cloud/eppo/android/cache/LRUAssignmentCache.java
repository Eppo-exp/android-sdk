package cloud.eppo.android.cache;

import android.util.LruCache;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.api.AbstractAssignmentCache;

public class LRUAssignmentCache extends AbstractAssignmentCache {
  public LRUAssignmentCache(int maxCacheSize) {
    super(
        new CacheDelegate() {
          private final LruCache<String, String> cache = new LruCache<>(maxCacheSize);

          @Override
          public void put(String cacheKey, @NonNull String serializedEntry) {
            cache.put(cacheKey, serializedEntry);
          }

          @Nullable @Override
          public String get(String cacheKey) {
            return cache.get(cacheKey);
          }

          @Override
          public boolean putIfAbsent(String cacheKey, @NonNull String serializedEntry) {
            boolean hadNoPreviousEntry;
            synchronized (cache) {
              String entry = cache.get(cacheKey);
              hadNoPreviousEntry = entry == null;
              if (hadNoPreviousEntry) {
                cache.put(cacheKey, serializedEntry);
              }
            }
            return hadNoPreviousEntry;
          }
        });
  }
}
