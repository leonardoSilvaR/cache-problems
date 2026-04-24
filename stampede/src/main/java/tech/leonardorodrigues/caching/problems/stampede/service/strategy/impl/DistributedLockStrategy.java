package tech.leonardorodrigues.caching.problems.stampede.service.strategy.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import tech.leonardorodrigues.caching.problems.stampede.entity.CacheEntry;
import tech.leonardorodrigues.caching.problems.stampede.entity.PreventionResult;
import tech.leonardorodrigues.caching.problems.stampede.service.CacheService;
import tech.leonardorodrigues.caching.problems.stampede.service.strategy.StampedePreventionService;
import tech.leonardorodrigues.caching.problems.stampede.utils.PreventionStrategy;

import java.util.function.Supplier;

@Component("DLOCK")
public class DistributedLockStrategy implements StampedePreventionService {

    private static final String LOCK_SUFFIX = ":lock";

    private final CacheService cacheService;
    private final MeterRegistry meterRegistry;
    private final RedissonClient redissonClient;

    public DistributedLockStrategy(CacheService cacheService, MeterRegistry meterRegistry, RedissonClient redissonClient) {
        this.cacheService = cacheService;
        this.meterRegistry = meterRegistry;
        this.redissonClient = redissonClient;
    }

    @Override
    public PreventionResult retrieve(String key, Supplier<Object> loader, Tags tags) {
        var cached = cacheService.get(key, CacheEntry.class);
        if (cached != null) {
            meterRegistry.counter("cache.hit", tags).increment();
            return new PreventionResult(cached.value(), true);
        }

        RLock lock = redissonClient.getLock(key + LOCK_SUFFIX);
        lock.lock();
        meterRegistry.counter("lock.aquired", tags).increment();
        try {
            // double-check: lock-holder may have already populated cache
            cached = cacheService.get(key, CacheEntry.class);
            if (cached != null) {
                meterRegistry.counter("cache.hit", tags).increment();
                return new PreventionResult(cached.value(), true);
            }

            var value = loader.get();
            cacheService.save(key, CacheEntry.of(value));
            meterRegistry.counter("cache.miss", tags).increment();
            return new PreventionResult(value, false);
        } finally {
            meterRegistry.counter("lock.released", tags).increment();
            lock.unlock();
        }
    }

    @Override
    public String strategy() {
        return PreventionStrategy.DLOCK.name();
    }
}
