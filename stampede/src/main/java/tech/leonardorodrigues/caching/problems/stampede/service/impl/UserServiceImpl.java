package tech.leonardorodrigues.caching.problems.stampede.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserEmail;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserRequest;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserResponse;
import tech.leonardorodrigues.caching.problems.stampede.entity.PreventionResult;
import tech.leonardorodrigues.caching.problems.stampede.repository.UserRepository;
import tech.leonardorodrigues.caching.problems.stampede.service.CacheService;
import tech.leonardorodrigues.caching.problems.stampede.service.UserService;
import tech.leonardorodrigues.caching.problems.stampede.service.strategy.StampedePreventionService;
import tech.leonardorodrigues.caching.problems.stampede.utils.CacheKeyEnum;
import tech.leonardorodrigues.caching.problems.stampede.utils.PreventionStrategy;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final MeterRegistry meterRegistry;
    private final UserRepository repository;
    private final CacheService cacheService;
    private final Map<String, StampedePreventionService> strategies;

    @Value("${app.db.simulated-latency-ms:0}")
    private long simulatedLatencyMs;

    public UserServiceImpl(
            MeterRegistry meterRegistry,
            UserRepository repository,
            CacheService cacheService,
            Map<String, StampedePreventionService> strategies) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
        this.cacheService = cacheService;
        this.strategies = strategies;
    }

    @Override
    public List<UserResponse> getAll() {
        return repository.findAll().stream().map(UserResponse::toResponse).toList();
    }

    @Override
    public UserResponse save(UserRequest request) {
        var savedUser = repository.save(request.toEntity(request));
        return UserResponse.toResponse(savedUser);
    }

    @Override
    public UserEmail getUserEmail(UUID userId, String phase, String strategy) {
        var cacheKey = cacheKey(userId);
        var isStampede = strategy.isBlank();
        var strat = isStampede ? "STAMPEDE" : PreventionStrategy.valueOf(strategy.toUpperCase()).name();
        var tags = Tags.of("phase", phase, "strategy", strat);

        var result = meterRegistry.timer("prevention.execution.timer", tags)
                .record(() -> {
                    if (isStampede) {
                        var cached = cacheService.get(cacheKey, String.class);
                        if (cached != null) {
                            meterRegistry.counter("cache.hit", tags).increment();
                            return new PreventionResult(cached, true);
                        }
                        var email = getUserEmailFromDatabase(userId, tags);
                        meterRegistry.counter("cache.miss", tags).increment();
                        cacheService.save(cacheKey, email);
                        return new PreventionResult(email, false);
                    } else {
                        return strategies.get(strat).retrieve(cacheKey, () -> getUserEmailFromDatabase(userId, tags), tags);
                    }
                });

        return new UserEmail(result.data().toString(), result.isCached());
    }

    @Override
    public void updateUserEmail(UUID userId, String email) {
        var user = repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        user.setEmail(email);

        //Write + invalidate
        repository.save(user);
        cacheService.invalidate(cacheKey(userId));
    }

    /**
     *
     * Forcing DB Latency for testing purposes
     *
     * @param id
     * @param tags
     * @return
     */
    private String getUserEmailFromDatabase(UUID id, Tags tags) {
        meterRegistry.counter("db.calls", tags).increment();
        return repository.findEmailWithLatency(id, simulatedLatencyMs / 1000.0)
                .orElseThrow(() -> new NoSuchElementException("Email not found for user id: " + id));
    }

    private String cacheKey(UUID userId) {
        return CacheKeyEnum.USER_KEY.getKey().concat(userId.toString());
    }
}
