package com.osgateway.ussd.infrastructure.persistence;

import com.osgateway.ussd.domain.UssdTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UssdTemplateRepository extends JpaRepository<UssdTemplate, Long> {
    List<UssdTemplate> findByOperatorId(Long operatorId);

    Optional<UssdTemplate> findFirstByOperatorIdAndTransactionTypeAndActiveTrue(Long operatorId, String type);

    boolean existsByOperatorIdAndTransactionTypeAndNameAndIdNot(
            Long operatorId, String type, String name, Long id);

    boolean existsByOperatorIdAndTransactionTypeAndName(
            Long operatorId, String type, String name);
}
