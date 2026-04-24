package tech.leonardorodrigues.caching.utils;

public enum PreventionStrategy {
    /**
     * Local Lock
     */
    MUTEX,
    /**
     * Distributed Lock
     */
    DLOCK,
    /**
     * TTL Jitter
     */
    JITTER,
    /**
     * Probabilistic Early Expiration
     */
    PEE,
    /**
     * Stale While Revalidate
     */
    SWR;
}
