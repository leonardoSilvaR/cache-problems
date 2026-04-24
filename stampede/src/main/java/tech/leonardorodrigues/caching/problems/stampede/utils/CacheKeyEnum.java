package tech.leonardorodrigues.caching.problems.stampede.utils;

public enum CacheKeyEnum {
    USER_KEY("user-email:");


    private String key;

    CacheKeyEnum(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
