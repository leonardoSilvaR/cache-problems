package tech.leonardorodrigues.caching.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record UserEmail(
        String email,
        @JsonIgnore boolean isCached
) {

}
