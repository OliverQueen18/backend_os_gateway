package com.osgateway.scheduler.application;

import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.scheduler.domain.SchedulerJob;
import com.osgateway.scheduler.infrastructure.client.GatewayClient;
import com.osgateway.scheduler.infrastructure.client.TransactionClient;
import com.osgateway.scheduler.infrastructure.persistence.SchedulerJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchScheduler {

    private final TransactionClient transactionClient;
    private final GatewayClient gatewayClient;
    private final RabbitTemplate rabbitTemplate;
    private final SchedulerJobRepository jobRepository;

    @Scheduled(fixedDelayString = "${osgateway.scheduler.poll-ms:5000}")
    public void pollAndDispatch() {
        SchedulerJob job = jobRepository.save(SchedulerJob.builder()
                .name("poll-pending-transactions")
                .status("RUNNING")
                .startedAt(Instant.now())
                .build());
        int processed = 0;
        try {
            var response = transactionClient.list("QUEUED", 0, 50);
            List<Map<String, Object>> content = response.getData() != null ? response.getData().getContent() : List.of();
            if (content == null) {
                content = List.of();
            }
            for (Map<String, Object> tx : content) {
                try {
                    if (dispatchOne(tx)) {
                        processed++;
                    }
                } catch (Exception ex) {
                    log.warn("Scheduler skip tx {}: {}", tx.get("id"), ex.getMessage());
                }
            }
            job.setStatus("SUCCESS");
            job.setDetails("Processed=" + processed);
        } catch (Exception ex) {
            log.warn("Scheduler poll failed: {}", ex.getMessage());
            job.setStatus("FAILED");
            job.setDetails(ex.getMessage());
        } finally {
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private boolean dispatchOne(Map<String, Object> tx) {
        if (tx == null || tx.get("id") == null) {
            return false;
        }
        Long txId = Long.valueOf(tx.get("id").toString());
        String operator = tx.get("operator") != null ? tx.get("operator").toString() : null;
        String type = tx.get("type") != null ? tx.get("type").toString() : null;
        var gatewayResp = gatewayClient.select(operator);
        if (gatewayResp == null || gatewayResp.getData() == null || gatewayResp.getData().get("id") == null) {
            log.warn("No gateway selected for tx {} operator={}", txId, operator);
            return false;
        }
        Long gatewayId = Long.valueOf(gatewayResp.getData().get("id").toString());
        Map<String, Object> ussdJob = new LinkedHashMap<>();
        ussdJob.put("transactionId", txId);
        ussdJob.put("gatewayId", gatewayId);
        ussdJob.put("operator", operator);
        ussdJob.put("type", type);
        // Publier d'abord : évite ASSIGNED sans job si Rabbit échoue
        rabbitTemplate.convertAndSend(QueueConstants.EXCHANGE, QueueConstants.USSD_ROUTING_KEY, ussdJob);
        transactionClient.updateStatus(txId, Map.of(
                "status", "ASSIGNED",
                "gatewayId", gatewayId,
                "note", "Assigned by scheduler"
        ));
        return true;
    }
}
