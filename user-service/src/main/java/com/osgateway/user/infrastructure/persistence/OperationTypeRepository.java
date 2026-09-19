package com.osgateway.user.infrastructure.persistence;

import com.osgateway.user.domain.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperationTypeRepository extends JpaRepository<OperationType, Long> {
    Optional<OperationType> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<OperationType> findByActiveTrueOrderByLabelAsc();
    List<OperationType> findAllByOrderByLabelAsc();
}
