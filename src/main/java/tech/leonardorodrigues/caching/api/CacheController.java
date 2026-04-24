package tech.leonardorodrigues.caching.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.leonardorodrigues.caching.service.CacheService;
import tech.leonardorodrigues.caching.utils.CacheKeyEnum;

import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class CacheController {

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @DeleteMapping("/{id}/cache:invalidate")
    public ResponseEntity<Object> invalidateCache(@PathVariable String id) {
        cacheService.invalidate(CacheKeyEnum.USER_KEY.getKey() + UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
