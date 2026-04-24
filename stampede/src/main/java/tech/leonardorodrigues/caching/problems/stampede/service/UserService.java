package tech.leonardorodrigues.caching.problems.stampede.service;

import tech.leonardorodrigues.caching.problems.stampede.api.data.UserEmail;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserRequest;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<UserResponse> getAll();

    UserResponse save(UserRequest request);

    UserEmail getUserEmail(UUID userId, String phase, String strategy);

    void updateUserEmail(UUID userId, String email);
}
