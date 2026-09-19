package com.osgateway.transaction.infrastructure.persistence;

import com.osgateway.transaction.domain.CancellationReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CancellationReasonRepository extends JpaRepository<CancellationReason, Long> {
    Optional<CancellationReason> findByCodeIgnoreCase(String code);

    List<CancellationReason> findByActiveTrueOrderByLabelAsc();

    List<CancellationReason> findAllByOrderByLabelAsc();

    boolean existsByCodeIgnoreCase(String code);
}
