package com.osgateway.sms.application;

import com.osgateway.common.dto.PageResponse;
import com.osgateway.common.enums.Priority;
import com.osgateway.common.enums.SmsStatus;
import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.sms.domain.*;
import com.osgateway.sms.infrastructure.persistence.*;
import lombok.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsRepository smsRepository;
    private final SmsHistoryRepository historyRepository;
    private final AutoReplyRuleRepository ruleRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public SmsMessage send(SendRequest request) {
        if (request.getRecipient() == null || request.getRecipient().isBlank()) {
            throw new com.osgateway.common.exception.BusinessException(
                    com.osgateway.common.exception.ErrorCode.VALIDATION_ERROR, "recipient is required");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new com.osgateway.common.exception.BusinessException(
                    com.osgateway.common.exception.ErrorCode.VALIDATION_ERROR, "content is required");
        }
        SmsMessage sms = SmsMessage.builder()
                .recipient(request.getRecipient().trim())
                .content(request.getContent())
                .status(SmsStatus.QUEUED)
                .priority(request.getPriority() != null ? request.getPriority() : Priority.NORMAL)
                .gatewayId(request.getGatewayId())
                .build();
        sms.setCreatedBy("api");
        sms = smsRepository.save(sms);
        history(sms.getId(), SmsStatus.QUEUED.name(), "Queued for dispatch");
        publish(sms);
        return sms;
    }

    @Transactional
    public List<SmsMessage> bulk(BulkRequest request) {
        List<SmsMessage> result = new ArrayList<>();
        for (String recipient : request.getRecipients()) {
            SendRequest one = new SendRequest();
            one.setRecipient(recipient);
            one.setContent(request.getContent());
            one.setPriority(request.getPriority());
            one.setGatewayId(request.getGatewayId());
            result.add(send(one));
        }
        return result;
    }

    @Transactional
    public SmsMessage schedule(ScheduleRequest request) {
        SmsMessage sms = SmsMessage.builder()
                .recipient(request.getRecipient())
                .content(request.getContent())
                .status(SmsStatus.SCHEDULED)
                .priority(request.getPriority() != null ? request.getPriority() : Priority.NORMAL)
                .scheduledAt(request.getScheduledAt())
                .build();
        sms.setCreatedBy("api");
        sms = smsRepository.save(sms);
        history(sms.getId(), SmsStatus.SCHEDULED.name(), "Scheduled at " + request.getScheduledAt());
        return sms;
    }

    @Transactional(readOnly = true)
    public PageResponse<SmsMessage> history(String recipient, int page, int size) {
        Page<SmsMessage> result = recipient == null || recipient.isBlank()
                ? smsRepository.findAll(PageRequest.of(page, size))
                : smsRepository.findByRecipientContainingIgnoreCase(recipient, PageRequest.of(page, size));
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    @Transactional
    public void processJob(Map<String, Object> job) {
        Long smsId = Long.valueOf(job.get("smsId").toString());
        SmsMessage sms = smsRepository.findById(smsId).orElse(null);
        if (sms == null) return;
        sms.setStatus(SmsStatus.SENT);
        sms.setSentAt(Instant.now());
        smsRepository.save(sms);
        history(smsId, SmsStatus.SENT.name(), "Dispatched to gateway");
    }

    @Transactional(readOnly = true)
    public String matchAutoReply(String incoming) {
        return ruleRepository.findByActiveTrue().stream()
                .filter(r -> incoming != null && incoming.matches(r.getMatchPattern()))
                .map(AutoReplyRule::getReplyContent)
                .findFirst()
                .orElse(null);
    }

    /**
     * Rapport gateway (SMS entrant opérateur ou résultat d’envoi).
     * Payload mobile : direction, address, body, timestamp, taskId?, status?
     */
    @Transactional
    public Map<String, String> reportFromGateway(ReportRequest request) {
        if (request == null) {
            throw new com.osgateway.common.exception.BusinessException(
                    com.osgateway.common.exception.ErrorCode.VALIDATION_ERROR, "body is required");
        }
        String direction = request.getDirection() != null ? request.getDirection().trim().toUpperCase() : "INBOUND";
        String address = request.getAddress() != null ? request.getAddress().trim() : "";
        String body = request.getBody() != null ? request.getBody() : "";
        if (address.isBlank() && body.isBlank()) {
            throw new com.osgateway.common.exception.BusinessException(
                    com.osgateway.common.exception.ErrorCode.VALIDATION_ERROR, "address or body is required");
        }
        Instant at = request.getTimestamp() != null && request.getTimestamp() > 0
                ? Instant.ofEpochMilli(request.getTimestamp())
                : Instant.now();

        if ("OUTBOUND".equals(direction)) {
            return reportOutbound(request, address, body, at);
        }
        return reportInbound(address, body, at, request.getTaskId());
    }

    private Map<String, String> reportInbound(String address, String body, Instant at, String taskId) {
        String recipient = truncate(address.isBlank() ? "unknown" : address, 30);
        String content = body.isBlank() ? "(empty)" : body;
        SmsMessage sms = SmsMessage.builder()
                .recipient(recipient)
                .content(content)
                .status(SmsStatus.RECEIVED)
                .priority(Priority.NORMAL)
                .sentAt(at)
                .build();
        sms.setCreatedBy("gateway-inbound");
        sms = smsRepository.save(sms);
        String details = "INBOUND" + (taskId != null && !taskId.isBlank() ? " task=" + taskId : "");
        history(sms.getId(), SmsStatus.RECEIVED.name(), details);
        return Map.of(
                "id", String.valueOf(sms.getId()),
                "status", SmsStatus.RECEIVED.name(),
                "direction", "INBOUND"
        );
    }

    private Map<String, String> reportOutbound(ReportRequest request, String address, String body, Instant at) {
        SmsStatus status = "FAILED".equalsIgnoreCase(request.getStatus()) ? SmsStatus.FAILED : SmsStatus.SENT;
        Long smsId = parseSmsId(request.getTaskId());
        if (smsId != null) {
            SmsMessage existing = smsRepository.findById(smsId).orElse(null);
            if (existing != null) {
                existing.setStatus(status);
                if (status == SmsStatus.SENT || status == SmsStatus.FAILED) {
                    existing.setSentAt(at);
                }
                smsRepository.save(existing);
                history(existing.getId(), status.name(), "Gateway outbound report");
                return Map.of(
                        "id", String.valueOf(existing.getId()),
                        "status", status.name(),
                        "direction", "OUTBOUND"
                );
            }
        }
        String recipient = truncate(address.isBlank() ? "unknown" : address, 30);
        SmsMessage sms = SmsMessage.builder()
                .recipient(recipient)
                .content(body.isBlank() ? "(empty)" : body)
                .status(status)
                .priority(Priority.NORMAL)
                .sentAt(at)
                .build();
        sms.setCreatedBy("gateway-outbound");
        sms = smsRepository.save(sms);
        history(sms.getId(), status.name(), "Gateway outbound report (no prior sms row)");
        return Map.of(
                "id", String.valueOf(sms.getId()),
                "status", status.name(),
                "direction", "OUTBOUND"
        );
    }

    private static Long parseSmsId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        String raw = taskId.trim();
        if (raw.regionMatches(true, 0, "sms-", 0, 4)) {
            raw = raw.substring(4);
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void publish(SmsMessage sms) {
        rabbitTemplate.convertAndSend(QueueConstants.EXCHANGE, QueueConstants.SMS_ROUTING_KEY,
                Map.of("smsId", sms.getId(), "recipient", sms.getRecipient(), "content", sms.getContent()),
                message -> {
                    message.getMessageProperties().setPriority(sms.getPriority().getRabbitPriority());
                    return message;
                });
    }

    private void history(Long smsId, String status, String details) {
        historyRepository.save(SmsHistory.builder()
                .smsId(smsId).status(status).details(details).createdAt(Instant.now()).build());
    }

    @Data
    public static class SendRequest {
        @com.fasterxml.jackson.annotation.JsonAlias({"to", "phone"})
        private String recipient;
        @com.fasterxml.jackson.annotation.JsonAlias({"body", "message", "text"})
        private String content;
        private Priority priority;
        private Long gatewayId;
    }

    @Data
    public static class BulkRequest {
        private List<String> recipients;
        @com.fasterxml.jackson.annotation.JsonAlias({"body", "message", "text"})
        private String content;
        private Priority priority;
        private Long gatewayId;
    }

    @Data
    public static class ScheduleRequest {
        @com.fasterxml.jackson.annotation.JsonAlias({"to", "phone"})
        private String recipient;
        @com.fasterxml.jackson.annotation.JsonAlias({"body", "message", "text"})
        private String content;
        private Priority priority;
        private Instant scheduledAt;
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportRequest {
        private String direction;
        private String address;
        @com.fasterxml.jackson.annotation.JsonAlias({"content", "message", "text"})
        private String body;
        private Long timestamp;
        private String taskId;
        private String status;
    }
}