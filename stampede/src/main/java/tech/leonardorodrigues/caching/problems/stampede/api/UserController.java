package tech.leonardorodrigues.caching.problems.stampede.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserEmail;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserRequest;
import tech.leonardorodrigues.caching.problems.stampede.api.data.UserResponse;
import tech.leonardorodrigues.caching.problems.stampede.service.UserService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> retrieveUsers() {
        return ResponseEntity.ok(userService.getAll());
    }

    @PostMapping
    public ResponseEntity<Object> saveUser(@RequestBody UserRequest request, HttpServletRequest httpRequest) {
        var savedUser = userService.save(request);
        return ResponseEntity.created(
                UriComponentsBuilder
                        .fromUri(URI.create(httpRequest.getRequestURL().toString()))
                        .path("/{id}")
                        .buildAndExpand(savedUser.id())
                        .toUri()
        ).build();
    }

    @GetMapping("/{id}/email")
    public ResponseEntity<UserEmail> retrieveUserEmail(
            @PathVariable String id,
            @RequestHeader(value = "X-Strategy", defaultValue = "") String strategy,
            HttpServletRequest httpRequest) {

        var phase = httpRequest.getHeader("phase") != null ? httpRequest.getHeader("phase") : "";
        var email = userService.getUserEmail(UUID.fromString(id), phase, strategy);

        var headers = new HttpHeaders();
        headers.add("X-Cache", email.isCached() ? "hit" : "miss");
        headers.add("X-Strategy", strategy);

        return new ResponseEntity<>(email, headers, 200);
    }

    @PatchMapping("/{id}/email")
    public ResponseEntity<Void> updateUserEmail(@PathVariable String id, @RequestBody UserEmail body) {
        userService.updateUserEmail(UUID.fromString(id), body.email());
        return ResponseEntity.noContent().build();
    }
}
