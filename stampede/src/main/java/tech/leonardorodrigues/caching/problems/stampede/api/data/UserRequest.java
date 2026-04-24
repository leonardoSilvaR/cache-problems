package tech.leonardorodrigues.caching.problems.stampede.api.data;

import tech.leonardorodrigues.caching.problems.stampede.entity.User;

public record UserRequest(
        String name,
        int age,
        String email
) {

    public User toEntity(UserRequest request) {
        var user = new User();
        user.setName(request.name);
        user.setAge(request.age);
        user.setEmail(request.email);
        return user;
    }
}