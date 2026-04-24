package tech.leonardorodrigues.caching.problems.stampede.service.strategy.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.leonardorodrigues.caching.problems.stampede.entity.CacheEntry;
import tech.leonardorodrigues.caching.problems.stampede.entity.PreventionResult;
import tech.leonardorodrigues.caching.problems.stampede.service.CacheService;
import tech.leonardorodrigues.caching.problems.stampede.service.strategy.StampedePreventionService;
import tech.leonardorodrigues.caching.problems.stampede.utils.PreventionStrategy;

import java.time.Duration;
import java.util.Random;
import java.util.function.Supplier;

@Component("JITTER")
public class JitterStrategy implements StampedePreventionService {

    private final CacheService cacheService;
    private final MeterRegistry meterRegistry;
    private final Random random = new Random();

    @Value("${app.cache.base-ttl-minutes}")
    private long baseTtlMinutes;

    @Value("${app.jitter.max-seconds}")
    private int maxJitterSeconds;

    public JitterStrategy(CacheService cacheService, MeterRegistry meterRegistry) {
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

        var value = loader.get();

        //Random TTL Jitter strategy
        Duration ttl = Duration.ofMinutes(baseTtlMinutes)
                .plusSeconds(random.nextInt(maxJitterSeconds));

        cacheService.save(key, CacheEntry.of(value), ttl);
        meterRegistry.counter("cache.miss", tags).increment();
        return new PreventionResult(value, false);
    }

    @Override
    public String strategy() {
        return PreventionStrategy.JITTER.name();
    }
}
