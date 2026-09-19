package com.osgateway.notification.application;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.osgateway.notification.config.FirebaseConfig.FirebaseMessagingHolder;
import com.osgateway.notification.domain.DevicePushToken;
import com.osgateway.notification.infrastructure.persistence.DevicePushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final DevicePushTokenRepository tokenRepository;
    private final FirebaseMessagingHolder firebaseMessagingHolder;

    @Transactional
    public void send(Long userId, String title, String body) {
        if (userId == null) {
            log.debug("FCM skip: no userId");
            return;
        }
        if (!firebaseMessagingHolder.isEnabled()) {
            log.info("FCM push (disabled) userId={} title={}", userId, title);
            return;
        }
        FirebaseMessaging messaging = firebaseMessagingHolder.messaging();
        List<DevicePushToken> tokens = tokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("FCM skip: no tokens for userId={}", userId);
            return;
        }
        String safeTitle = title != null ? title : "OS Gateway";
        String safeBody = body != null ? body : "";
        List<String> invalid = new ArrayList<>();
        for (DevicePushToken device : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(device.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(safeTitle)
                                .setBody(safeBody)
                                .build())
                        .putData("title", safeTitle)
                        .putData("message", safeBody)
                        .putData("body", safeBody)
                        .build();
                String messageId = messaging.send(message);
                log.info("FCM sent userId={} messageId={}", userId, messageId);
            } catch (FirebaseMessagingException e) {
                log.warn("FCM send failed userId={} code={}: {}", userId, e.getMessagingErrorCode(), e.getMessage());
                if (isInvalidToken(e)) {
                    invalid.add(device.getToken());
                }
            } catch (Exception e) {
                log.warn("FCM send failed userId={}: {}", userId, e.getMessage());
            }
        }
        for (String token : invalid) {
            tokenRepository.findByToken(token).ifPresent(tokenRepository::delete);
            log.info("Removed invalid FCM token for userId={}", userId);
        }
    }

    private static boolean isInvalidToken(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
