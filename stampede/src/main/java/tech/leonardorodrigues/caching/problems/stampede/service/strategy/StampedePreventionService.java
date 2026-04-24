package tech.leonardorodrigues.caching.problems.stampede.service.strategy;

import io.micrometer.core.instrument.Tags;
import tech.leonardorodrigues.caching.problems.stampede.entity.PreventionResult;

import java.util.function.Supplier;

public interface StampedePreventionService {
    PreventionResult retrieve(String key, Supplier<Object> loader, Tags tags);
    String strategy();
}
