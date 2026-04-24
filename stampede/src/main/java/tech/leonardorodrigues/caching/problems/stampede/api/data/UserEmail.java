package tech.leonardorodrigues.caching.problems.stampede.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record UserEmail(
        String email,
        @JsonIgnore boolean isCached
) {

}
