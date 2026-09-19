package com.osgateway.gatewaydevice.infrastructure.messaging;

import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.gatewaydevice.application.GatewayTaskQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UssdJobConsumer {

    private final GatewayTaskQueueService taskQueueService;

    @RabbitListener(queues = QueueConstants.USSD_QUEUE)
    public void onUssdJob(Map<String, Object> job) {
        log.info("USSD job received: {}", job);
        taskQueueService.enqueueFromUssdJob(job);
    }
}
