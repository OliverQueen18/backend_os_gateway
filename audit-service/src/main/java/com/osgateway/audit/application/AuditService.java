package com.osgateway.audit.application;

import com.osgateway.common.dto.PageResponse;
import com.osgateway.audit.domain.AuditLog;
import com.osgateway.audit.infrastructure.persistence.AuditLogRepository;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog append(AppendRequest request) {
        return auditLogRepository.save(AuditLog.builder()
                .actorId(request.getActorId())
                .actorName(request.getActorName())
                .action(request.getAction())
                .resourceType(request.getResourceType())
                .resourceId(request.getResourceId())
                .details(request.getDetails())
                .ipAddress(request.getIpAddress())
                .createdAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLog> search(String action, String actorName, String resourceType, int page, int size) {
        Page<AuditLog> result = auditLogRepository.search(action, actorName, resourceType, PageRequest.of(page, size));
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    @Data
    public static class AppendRequest {
        private Long actorId;
        private String actorName;
        private String action;
        private String resourceType;
        private String resourceId;
        private String details;
        private String ipAddress;
    }
}