package tech.leonardorodrigues.caching.service.strategy;

import io.micrometer.core.instrument.Tags;
import tech.leonardorodrigues.caching.entity.PreventionResult;
import tech.leonardorodrigues.caching.utils.PreventionStrategy;

import java.time.Duration;
import java.util.function.Supplier;

public interface StampedePreventionService {
    PreventionResult retrieve(String key, Supplier<Object> loader, Tags tags);
    String strategy();
}
