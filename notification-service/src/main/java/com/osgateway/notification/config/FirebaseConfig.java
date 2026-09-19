package com.osgateway.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${osgateway.firebase.credentials-json:}")
    private String credentialsJson;

    @Value("${osgateway.firebase.credentials-path:}")
    private String credentialsPath;

    @Bean
    public FirebaseMessagingHolder firebaseMessagingHolder() {
        try {
            GoogleCredentials credentials = loadCredentials();
            if (credentials == null) {
                log.warn("Firebase credentials not configured — FCM pushes will be skipped "
                        + "(set FIREBASE_CREDENTIALS_JSON or FIREBASE_CREDENTIALS_PATH)");
                return FirebaseMessagingHolder.disabled();
            }
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized");
            }
            return FirebaseMessagingHolder.enabled(FirebaseMessaging.getInstance());
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK — FCM pushes disabled: {}", e.getMessage());
            return FirebaseMessagingHolder.disabled();
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (StringUtils.hasText(credentialsJson)) {
            try (InputStream in = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (StringUtils.hasText(credentialsPath)) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        return null;
    }

    public record FirebaseMessagingHolder(FirebaseMessaging messaging) {
        public static FirebaseMessagingHolder disabled() {
            return new FirebaseMessagingHolder(null);
        }

        public static FirebaseMessagingHolder enabled(FirebaseMessaging messaging) {
            return new FirebaseMessagingHolder(messaging);
        }

        public boolean isEnabled() {
            return messaging != null;
        }
    }
}
