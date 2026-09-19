package com.osgateway.user.infrastructure.persistence;

import com.osgateway.user.domain.DistributorAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DistributorAttachmentRepository extends JpaRepository<DistributorAttachment, Long> {
    List<DistributorAttachment> findByDistributorIdOrderByCreatedAtDesc(Long distributorId);

    Optional<DistributorAttachment> findByIdAndDistributorId(Long id, Long distributorId);

    long countByDistributorId(Long distributorId);

    @Query("""
        SELECT a.distributorId, COUNT(a)
        FROM DistributorAttachment a
        WHERE a.distributorId IN :ids
        GROUP BY a.distributorId
        """)
    List<Object[]> countGroupedByDistributorIds(@Param("ids") Collection<Long> ids);
}
