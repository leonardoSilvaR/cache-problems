package tech.leonardorodrigues.caching.problems.stampede.api.data;

import tech.leonardorodrigues.caching.problems.stampede.entity.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        int age,
        String email
) {


    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getAge(),
                user.getEmail()
        );
    }
}