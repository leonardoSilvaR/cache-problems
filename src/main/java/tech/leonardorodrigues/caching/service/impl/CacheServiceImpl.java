package tech.leonardorodrigues.caching.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tech.leonardorodrigues.caching.service.CacheService;

import java.time.Duration;

@Service
public class CacheServiceImpl implements CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${app.cache.base-ttl-minutes}")
    private long baseTtlMinutes;


    public CacheServiceImpl(final RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void invalidate(String key) {
        meterRegistry.counter("cache.invalidate.count").increment();
        redisTemplate.delete(key);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        meterRegistry.counter("cache.get.count").increment();
        return type.cast(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void save(String key, Object data) {
        meterRegistry.counter("cache.save.count").increment();
        redisTemplate.opsForValue().set(key, data, Duration.ofMinutes(baseTtlMinutes));
    }

    @Override
    public void save(String key, Object data, Duration ttl) {
        meterRegistry.counter("cache.save.count").increment();
        redisTemplate.opsForValue().set(key, data, ttl);
    }

    @Override
    public Boolean saveIfAbsent(String key, Object data, Duration ttl) {
        meterRegistry.counter("cache.save.absent.count").increment();
        return redisTemplate.opsForValue().setIfAbsent(key, data, ttl);
    }

    @Override
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

}
