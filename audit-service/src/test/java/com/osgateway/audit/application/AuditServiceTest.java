package com.osgateway.audit.application;

import com.osgateway.audit.domain.AuditLog;
import com.osgateway.audit.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AuditService auditService;

    @Test
    void append_savesLog() {
        when(auditLogRepository.save(any())).thenAnswer(i -> {
            AuditLog log = i.getArgument(0);
            log.setId(7L);
            return log;
        });
        AuditService.AppendRequest req = new AuditService.AppendRequest();
        req.setAction("LOGIN");
        req.setActorName("admin");
        AuditLog log = auditService.append(req);
        assertEquals(7L, log.getId());
        assertEquals("LOGIN", log.getAction());
    }
}