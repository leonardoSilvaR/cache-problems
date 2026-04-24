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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component("PEE")
public class ProbabilisticEarlyExpirationStrategy implements StampedePreventionService {

    private static final double BETA = 1.0;

    private final CacheService cacheService;
    private final MeterRegistry meterRegistry;

    @Value("${app.cache.base-ttl-minutes}")
    private long baseTtlMinutes;

    public ProbabilisticEarlyExpirationStrategy(CacheService cacheService, MeterRegistry meterRegistry) {
        this.cacheService = cacheService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public PreventionResult retrieve(String key, Supplier<Object> loader, Tags tags) {
        var cached = cacheService.get(key, CacheEntry.class);

        if (cached != null) {

            if (!shouldRecompute(cached.deltaMs(), cached.expiryMs())) {
                meterRegistry.counter("cache.hit", tags).increment();
                return new PreventionResult(cached.value(), true);
            }

            meterRegistry.counter("cache.early.recompute", tags).increment();
        }

        long start = System.currentTimeMillis();
        var value = loader.get();
        long deltaElapsed = System.currentTimeMillis() - start;

        Duration ttl = Duration.ofMinutes(baseTtlMinutes);
        //Exact expiration moment
        long expiration = System.currentTimeMillis() + ttl.toMillis();

        cacheService.save(key, CacheEntry.forProbabilistic(value, deltaElapsed, expiration), ttl);
        meterRegistry.counter("cache.miss", tags).increment();
        return new PreventionResult(value, false);
    }

    /**
     * Recompute early if now + (-deltaElapsed) * beta * ln(rand) >= expiry.
     * Probability of early recompute rises non-linearly as expiry approaches.
     */
    private boolean shouldRecompute(long deltaElapsed, long expiration) {
        //U factor, random between 0.0 - 1.0
        double random = ThreadLocalRandom.current().nextDouble();
        double gap = -deltaElapsed * BETA * Math.log(random);
        return System.currentTimeMillis() + gap >= expiration;
    }

    @Override
    public String strategy() {
        return PreventionStrategy.PEE.name();
    }
}
