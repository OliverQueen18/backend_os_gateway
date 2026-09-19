package com.osgateway.sms.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SMS jobs are consumed by {@code gateway-service} which enqueues them on the Android
 * gateway. Keeping this class as a marker so older docs still find the package;
 * do not re-enable a competing {@code @RabbitListener} on {@code SMS_QUEUE}.
 */
@Slf4j
@Component
public class SmsJobConsumer {
}
