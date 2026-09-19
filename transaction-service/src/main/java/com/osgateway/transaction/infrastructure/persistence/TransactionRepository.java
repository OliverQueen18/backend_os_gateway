package com.osgateway.transaction.infrastructure.persistence;

import com.osgateway.common.enums.TransactionStatus;
import com.osgateway.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByReference(String reference);
    List<Transaction> findByStatus(TransactionStatus status);

    /**
     * Optional string filters must be empty string (never null) so Postgres does not bind
     * them as bytea under UPPER/equality. Date bounds must always be concrete Instant values
     * (use wide sentinels when unbounded) — {@code :from IS NULL} breaks PG type inference.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:operator = '' OR t.operator = :operator)
          AND (:type = '' OR t.type = :type)
          AND (:userId IS NULL OR t.userId = :userId)
          AND (:distributorId IS NULL OR t.distributorId = :distributorId)
          AND t.createdAt >= :from
          AND t.createdAt < :to
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> search(@Param("status") TransactionStatus status,
                             @Param("operator") String operator,
                             @Param("type") String type,
                             @Param("userId") Long userId,
                             @Param("distributorId") Long distributorId,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
