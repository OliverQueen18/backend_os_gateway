package com.osgateway.sms.infrastructure.persistence;

import com.osgateway.sms.domain.AutoReplyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutoReplyRuleRepository extends JpaRepository<AutoReplyRule, Long> {
    List<AutoReplyRule> findByActiveTrue();
}