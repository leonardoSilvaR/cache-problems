package tech.leonardorodrigues.caching.problems.stampede.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.leonardorodrigues.caching.problems.stampede.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query(value = "SELECT u.email FROM TB_USER u WHERE id = :id AND pg_sleep(:latencySeconds) IS NOT NULL",
           nativeQuery = true)
    Optional<String> findEmailWithLatency(@Param("id") UUID id, @Param("latencySeconds") double latencySeconds);
}
