package com.osgateway.audit.infrastructure.persistence;

import com.osgateway.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:action IS NULL OR a.action = :action)
          AND (:actorName IS NULL OR LOWER(a.actorName) LIKE LOWER(CONCAT('%', :actorName, '%')))
          AND (:resourceType IS NULL OR a.resourceType = :resourceType)
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("actorName") String actorName,
                          @Param("resourceType") String resourceType,
                          Pageable pageable);
}