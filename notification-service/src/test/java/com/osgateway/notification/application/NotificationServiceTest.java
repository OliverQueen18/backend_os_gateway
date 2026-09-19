package com.osgateway.notification.application;

import com.osgateway.notification.domain.Notification;
import com.osgateway.notification.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository notificationRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock FcmPushService fcmPushService;
    @InjectMocks NotificationService notificationService;

    @Test
    void createAlert_persists() {
        when(notificationRepository.save(any())).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setId(1L);
            return n;
        });
        NotificationService.AlertRequest req = new NotificationService.AlertRequest();
        req.setType("LOW_BATTERY");
        req.setTitle("Low battery");
        req.setMessage("Gateway battery < 20%");
        req.setGatewayId(5L);
        req.setUserId(9L);
        Notification n = notificationService.createAlert(req);
        assertEquals(1L, n.getId());
        assertEquals("LOW_BATTERY", n.getType());
        verify(fcmPushService).send(9L, "Low battery", "Gateway battery < 20%");
    }
}
