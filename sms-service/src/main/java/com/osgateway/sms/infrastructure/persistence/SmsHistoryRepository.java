package com.osgateway.sms.infrastructure.persistence;

import com.osgateway.sms.domain.SmsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsHistoryRepository extends JpaRepository<SmsHistory, Long> {}