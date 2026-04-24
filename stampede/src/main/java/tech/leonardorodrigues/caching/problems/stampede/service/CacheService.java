package tech.leonardorodrigues.caching.problems.stampede.service;

import java.time.Duration;

public interface CacheService {
    void invalidate(String key);
    <T> T get(String key, Class<T> type);
    void save(String key, Object data);
    void save(String key, Object data, Duration ttl);
    Boolean saveIfAbsent(String key, Object data, Duration ttl);
    Boolean hasKey(String key);
}
