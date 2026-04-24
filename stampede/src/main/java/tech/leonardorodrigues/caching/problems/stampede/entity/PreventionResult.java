package tech.leonardorodrigues.caching.problems.stampede.entity;

public record PreventionResult(
        Object data,
        boolean isCached
) {
}
