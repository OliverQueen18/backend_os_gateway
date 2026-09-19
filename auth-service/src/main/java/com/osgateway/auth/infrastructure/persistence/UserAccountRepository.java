package com.osgateway.auth.infrastructure.persistence;

import com.osgateway.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Match téléphone en ignorant espaces / + / tirets (numéro national ou E.164 digits).
     */
    @Query(value = """
            SELECT * FROM users
            WHERE phone IS NOT NULL AND phone <> ''
              AND (
                regexp_replace(phone, '[^0-9]', '', 'g') = :digits
                OR (
                  regexp_replace(phone, '[^0-9]', '', 'g') LIKE '%' || :digits
                  AND length(regexp_replace(phone, '[^0-9]', '', 'g')) - length(:digits) BETWEEN 1 AND 4
                )
              )
            ORDER BY id
            LIMIT 1
            """, nativeQuery = true)
    Optional<UserAccount> findByPhoneDigits(@Param("digits") String digits);

    @Query(value = """
            SELECT r.name FROM roles r
            JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId
            """, nativeQuery = true)
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
