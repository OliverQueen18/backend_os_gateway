package com.osgateway.ussd.infrastructure.persistence;

import com.osgateway.ussd.domain.UssdStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UssdStepRepository extends JpaRepository<UssdStep, Long> {
    List<UssdStep> findByTemplateIdOrderByStepOrderAsc(Long templateId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from UssdStep s where s.templateId = :templateId")
    @org.springframework.transaction.annotation.Transactional
    void deleteByTemplateId(@Param("templateId") Long templateId);
}