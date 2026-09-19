package com.osgateway.sms.infrastructure.persistence;

import com.osgateway.sms.domain.SmsMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsRepository extends JpaRepository<SmsMessage, Long> {
    Page<SmsMessage> findByRecipientContainingIgnoreCase(String recipient, Pageable pageable);
}