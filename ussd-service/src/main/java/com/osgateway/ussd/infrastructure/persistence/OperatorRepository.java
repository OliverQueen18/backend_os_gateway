package com.osgateway.ussd.infrastructure.persistence;

import com.osgateway.ussd.domain.Operator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperatorRepository extends JpaRepository<Operator, Long> {
    Optional<Operator> findByCode(String code);

    List<Operator> findByActiveTrueOrderByNameAsc();
}
