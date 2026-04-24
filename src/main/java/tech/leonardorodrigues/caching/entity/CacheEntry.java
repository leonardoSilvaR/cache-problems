package tech.leonardorodrigues.caching.entity;

import java.io.Serializable;

public record CacheEntry(
        Object value,
        long cachedAtMs,
        long deltaMs,
        long expiryMs
) implements Serializable {

    public static CacheEntry of(Object value) {
        return new CacheEntry(value, System.currentTimeMillis(), 0, 0);
    }

    public static CacheEntry forProbabilistic(Object value, long deltaMs, long expiryMs) {
        return new CacheEntry(value, System.currentTimeMillis(), deltaMs, expiryMs);
    }
}
