package com.osgateway.ussd.infrastructure.persistence;

import com.osgateway.ussd.domain.OperatorBalancePattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperatorBalancePatternRepository extends JpaRepository<OperatorBalancePattern, Long> {
    List<OperatorBalancePattern> findByOperatorIdOrderByFieldTypeAscPriorityAscIdAsc(Long operatorId);

    void deleteByOperatorId(Long operatorId);
}
