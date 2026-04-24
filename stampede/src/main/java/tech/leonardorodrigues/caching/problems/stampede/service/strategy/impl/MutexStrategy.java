package tech.leonardorodrigues.caching.problems.stampede.service.strategy.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import tech.leonardorodrigues.caching.problems.stampede.entity.CacheEntry;
import tech.leonardorodrigues.caching.problems.stampede.entity.PreventionResult;
import tech.leonardorodrigues.caching.problems.stampede.service.CacheService;
import tech.leonardorodrigues.caching.problems.stampede.service.strategy.StampedePreventionService;
import tech.leonardorodrigues.caching.problems.stampede.utils.PreventionStrategy;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component("MUTEX")
public class MutexStrategy implements StampedePreventionService {

    private final CacheService cacheService;
    private final MeterRegistry meterRegistry;
    private final ReentrantLock lock = new ReentrantLock();

    public MutexStrategy(CacheService cacheService, MeterRegistry meterRegistry) {
        this.cacheService = cacheService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public PreventionResult retrieve(String key, Supplier<Object> loader, Tags tags) {

        var cached = cacheService.get(key, CacheEntry.class);
        if (cached != null) {
            meterRegistry.counter("cache.hit", tags).increment();
            return new PreventionResult(cached.value(), true);
        }

        lock.lock();
        meterRegistry.counter("lock.aquired", tags).increment();
        try {
            // double-check: another thread may have populated cache while we waited,
            // because of thread pool queue
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
        return PreventionStrategy.MUTEX.name();
    }
}
