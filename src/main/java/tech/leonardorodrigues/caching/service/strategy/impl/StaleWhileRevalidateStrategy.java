package tech.leonardorodrigues.caching.service.strategy.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.leonardorodrigues.caching.entity.CacheEntry;
import tech.leonardorodrigues.caching.entity.PreventionResult;
import tech.leonardorodrigues.caching.service.CacheService;
import tech.leonardorodrigues.caching.service.strategy.StampedePreventionService;
import tech.leonardorodrigues.caching.utils.PreventionStrategy;

import java.time.Duration;
import java.util.function.Supplier;

@Component("SWR")
public class StaleWhileRevalidateStrategy implements StampedePreventionService {

    private static final String REFRESHING_SUFFIX = ":refreshing";

    private final CacheService cacheService;
    private final MeterRegistry meterRegistry;

    @Value("${app.swr.fresh-ttl-seconds:10}")
    private long freshTtlSeconds;

    public StaleWhileRevalidateStrategy(CacheService cacheService, MeterRegistry meterRegistry) {
        this.cacheService = cacheService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public PreventionResult retrieve(String key, Supplier<Object> loader, Tags tags) {
        var cached = cacheService.get(key, CacheEntry.class);

        if (cached == null) {
            var value = loader.get();
            cacheService.save(key, CacheEntry.of(value));
            meterRegistry.counter("cache.miss", tags).increment();
            return new PreventionResult(value, false);
        }

        long ageMs = System.currentTimeMillis() - cached.cachedAtMs();
        long freshTtlMs = Duration.ofSeconds(freshTtlSeconds).toMillis();

        if (ageMs >= freshTtlMs) {
            //Mechanism to ensure only one thread fire data update
            boolean shouldRefresh = Boolean.TRUE.equals(
                    cacheService.saveIfAbsent(key + REFRESHING_SUFFIX, "1", Duration.ofSeconds(freshTtlSeconds))
            );

            if (shouldRefresh) {
                Thread.ofVirtual().start(() -> {
                    var value = loader.get();
                    cacheService.save(key, CacheEntry.of(value));
                    cacheService.invalidate(key + REFRESHING_SUFFIX);
                });
                meterRegistry.counter("cache.revalidation", tags).increment();
            }
        }

        meterRegistry.counter("cache.hit", tags).increment();
        return new PreventionResult(cached.value(), true);
    }

    @Override
    public String strategy() {
        return PreventionStrategy.SWR.name();
    }
}
