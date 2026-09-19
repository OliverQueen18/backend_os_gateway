package com.osgateway.notification.application;

import com.osgateway.common.dto.PageResponse;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.notification.domain.Notification;
import com.osgateway.notification.infrastructure.persistence.NotificationRepository;
import lombok.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FcmPushService fcmPushService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Transactional
    public Notification createAlert(AlertRequest request) {
        Notification n = Notification.builder()
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .userId(request.getUserId())
                .gatewayId(request.getGatewayId())
                .severity(request.getSeverity() != null ? request.getSeverity() : "WARN")
                .readFlag(false)
                .build();
        n.setCreatedBy("system");
        n = notificationRepository.save(n);
        messagingTemplate.convertAndSend("/topic/notifications", n);
        broadcastSse(n);
        fcmPushService.send(request.getUserId(), request.getTitle(), request.getMessage());
        return n;
    }

    @RabbitListener(queues = QueueConstants.NOTIFICATION_QUEUE)
    public void onJob(Map<String, Object> job) {
        AlertRequest req = new AlertRequest();
        req.setType(String.valueOf(job.getOrDefault("type", "SYSTEM")));
        req.setTitle(String.valueOf(job.getOrDefault("title", "Alert")));
        req.setMessage(String.valueOf(job.getOrDefault("message", "")));
        if (job.get("userId") != null) req.setUserId(Long.valueOf(job.get("userId").toString()));
        if (job.get("gatewayId") != null) req.setGatewayId(Long.valueOf(job.get("gatewayId").toString()));
        req.setSeverity(String.valueOf(job.getOrDefault("severity", "WARN")));
        createAlert(req);
    }

    @Transactional(readOnly = true)
    public PageResponse<Notification> list(Long userId, int page, int size) {
        Page<Notification> result = userId == null
                ? notificationRepository.findAll(PageRequest.of(page, size))
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    @Transactional
    public Notification acknowledge(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Notification not found"));
        n.setReadFlag(true);
        return notificationRepository.save(n);
    }

    @Transactional
    public int acknowledgeAll(Long userId) {
        Page<Notification> page = userId == null
                ? notificationRepository.findAll(PageRequest.of(0, 500))
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 500));
        int count = 0;
        for (Notification n : page.getContent()) {
            if (!n.isReadFlag()) {
                n.setReadFlag(true);
                notificationRepository.save(n);
                count++;
            }
        }
        return count;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    private void broadcastSse(Notification n) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(n));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    @Data
    public static class AlertRequest {
        private String type;
        private String title;
        private String message;
        private Long userId;
        private Long gatewayId;
        private String severity;
    }
}
