package com.osgateway.user.infrastructure.persistence;

import com.osgateway.user.domain.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    Page<UserEntity> findByEnabled(boolean enabled, Pageable pageable);

    /**
     * Search by username/email/fullName. {@code q} must be non-null (use empty string for no text filter)
     * to avoid PostgreSQL {@code lower(bytea)} errors when binding null strings.
     */
    @Query("""
        SELECT u FROM UserEntity u
        WHERE (
                :q = ''
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
          AND (:enabled IS NULL OR u.enabled = :enabled)
        """)
    Page<UserEntity> search(@Param("q") String q, @Param("enabled") Boolean enabled, Pageable pageable);
}
