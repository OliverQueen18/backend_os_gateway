package com.osgateway.gatewaydevice.application;

import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.common.util.PhoneNumbers;
import com.osgateway.common.util.UvBalanceEffect;
import com.osgateway.gatewaydevice.api.dto.GatewayTaskDtos.BalancePatternDto;
import com.osgateway.gatewaydevice.api.dto.GatewayTaskDtos.GatewayTask;
import com.osgateway.gatewaydevice.api.dto.GatewayTaskDtos.TaskResultRequest;
import com.osgateway.gatewaydevice.api.dto.GatewayTaskDtos.UssdStepDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * File de tâches par gateway (alimentée par RabbitMQ ussd.job).
 */
@Slf4j
@Service
public class GatewayTaskQueueService {

    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<GatewayTask>> queues = new ConcurrentHashMap<>();
    private final Set<String> dispatchedTaskIds = ConcurrentHashMap.newKeySet();
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    /** Délai max d'attente SMS opérateur avant TIMEOUT (retard réseau). */
    @Value("${osgateway.sms-confirm.expire-minutes:15}")
    private int smsConfirmExpireMinutes = 15;

    public GatewayTaskQueueService(JdbcTemplate jdbcTemplate, RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enqueue(Long gatewayId, GatewayTask task) {
        if (gatewayId == null || task == null) {
            return;
        }
        if (task.getId() != null) {
            dispatchedTaskIds.add(task.getId());
        }
        queues.computeIfAbsent(gatewayId, id -> new ConcurrentLinkedQueue<>()).offer(task);
        log.info("Task {} queued for gateway {}", task.getId(), gatewayId);
    }

    public void enqueueFromUssdJob(Map<String, Object> job) {
        if (job == null) {
            return;
        }
        String txId = job.get("transactionId") != null ? job.get("transactionId").toString() : null;
        String operator = job.get("operator") != null ? job.get("operator").toString() : null;
        String type = job.get("type") != null ? job.get("type").toString() : null;
        String taskId = "ussd-" + (txId != null ? txId : UUID.randomUUID());
        if (isDuplicate(taskId)) {
            log.info("USSD task {} already dispatched, skip", taskId);
            return;
        }

        // Prefer a live heartbeat over the scheduler-assigned id (seed ONLINE rows steal jobs).
        Long gatewayId = resolveOnlineGatewayId(operator);
        if (gatewayId == null && job.get("gatewayId") != null && !job.get("gatewayId").toString().isBlank()) {
            gatewayId = Long.valueOf(job.get("gatewayId").toString());
            log.warn("No recent-heartbeat gateway for operator {} — falling back to job gatewayId={}",
                    operator, gatewayId);
        }
        if (gatewayId == null) {
            log.warn("No ONLINE gateway for USSD job tx={} operator={}", txId, operator);
            return;
        }

        String phone = null;
        Double amount = null;
        Map<String, String> variables = new LinkedHashMap<>();

        if (txId != null) {
            try {
                Map<String, Object> row = jdbcTemplate.queryForMap(
                        """
                        SELECT beneficiary_phone, amount, operator, type
                        FROM transactions WHERE id = ?
                        """,
                        Long.valueOf(txId)
                );
                phone = row.get("beneficiary_phone") != null ? row.get("beneficiary_phone").toString() : null;
                // USSD : numéro national sans indicatif (ex. 70… et non +22370…)
                if (phone != null) {
                    phone = PhoneNumbers.toNationalDigits(phone);
                }
                if (row.get("amount") != null) {
                    amount = Double.valueOf(row.get("amount").toString());
                }
                if (operator == null && row.get("operator") != null) {
                    operator = row.get("operator").toString();
                }
                if (type == null && row.get("type") != null) {
                    type = row.get("type").toString();
                }
                if (phone != null) {
                    variables.put("phone", phone);
                }
                if (amount != null) {
                    // Entier XOF uniquement — jamais "100.0" (le composeur USSD strippe le '.')
                    variables.put("amount", String.valueOf(Math.round(amount)));
                }
                if (type != null) {
                    variables.put("type", type);
                }
            } catch (Exception ex) {
                log.warn("Could not enrich task from transaction {}: {}", txId, ex.getMessage());
            }
        }

        String gatewayPin = loadGatewayUssdPin(gatewayId);
        if (gatewayPin != null) {
            variables.put("pin", gatewayPin);
        } else {
            log.warn("Gateway {} has no ussd_pin — {{pin}} will be empty in USSD templates", gatewayId);
        }

        List<UssdStepDto> steps = loadUssdSteps(operator, type, 0);
        List<BalancePatternDto> balancePatterns = loadBalancePatterns(operator);
        boolean moneyOp = type != null && !"SOLDE".equalsIgnoreCase(type);
        // Confirmation TX = delta solde uniquement (SOLDE avant/après) — pas de SMS
        List<UssdStepDto> balanceCheckSteps = moneyOp
                ? loadUssdSteps(operator, "SOLDE", 0)
                : List.of();
        if (moneyOp) {
            steps = stripSmsConfirmationSteps(steps);
        }
        String ussdCode = null;
        for (UssdStepDto step : steps) {
            if ("COMPOSE".equalsIgnoreCase(step.getAction()) && step.getValue() != null && !step.getValue().isBlank()) {
                ussdCode = step.getValue();
            }
        }

        GatewayTask task = GatewayTask.builder()
                .id(taskId)
                .type("USSD")
                .operator(operator)
                .transactionId(txId)
                .phone(phone)
                .amount(amount)
                .pin(gatewayPin)
                .ussdCode(ussdCode)
                .priority(5)
                .steps(steps)
                .variables(variables)
                .balanceCheckSteps(balanceCheckSteps)
                .balancePatterns(balancePatterns)
                .timeoutSeconds(120)
                .build();

        enqueue(gatewayId, task);
    }

    /** Retire WAIT_SMS / VERIFY_BALANCE du scénario TX — la vérif solde est hors bande. */
    private static List<UssdStepDto> stripSmsConfirmationSteps(List<UssdStepDto> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<UssdStepDto> out = new ArrayList<>();
        int order = 1;
        for (UssdStepDto step : steps) {
            String action = step.getAction();
            if (action == null) {
                continue;
            }
            if ("WAIT_SMS".equalsIgnoreCase(action)
                    || "VERIFY_BALANCE".equalsIgnoreCase(action)
                    || "RUN_TEMPLATE".equalsIgnoreCase(action)) {
                continue;
            }
            step.setOrder(order++);
            out.add(step);
        }
        return out;
    }

    private String loadGatewayUssdPin(Long gatewayId) {
        try {
            return jdbcTemplate.query(
                    "SELECT ussd_pin FROM gateways WHERE id = ?",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        String pin = rs.getString(1);
                        return pin != null && !pin.isBlank() ? pin.trim() : null;
                    },
                    gatewayId
            );
        } catch (Exception ex) {
            log.warn("Could not load ussd_pin for gateway {}: {}", gatewayId, ex.getMessage());
            return null;
        }
    }

    private List<UssdStepDto> loadUssdSteps(String operator, String type) {
        return loadUssdSteps(operator, type, 0);
    }

    private List<UssdStepDto> loadUssdSteps(String operator, String type, int depth) {
        if (operator == null || operator.isBlank() || type == null || type.isBlank()) {
            return List.of();
        }
        try {
            List<UssdStepDto> steps = jdbcTemplate.query(
                    """
                    SELECT s.step_order, s.action, s.expression, s.expected_pattern, s.extract_var, s.wait_millis
                    FROM ussd_steps s
                    WHERE s.template_id = (
                        SELECT t.id
                        FROM ussd_templates t
                        JOIN operators o ON o.id = t.operator_id
                        WHERE UPPER(o.code) = UPPER(?)
                          AND UPPER(t.transaction_type) = UPPER(?)
                          AND t.active = TRUE
                        ORDER BY t.id ASC
                        LIMIT 1
                    )
                    ORDER BY s.step_order ASC
                    """,
                    (rs, rowNum) -> {
                        String action = rs.getString("action");
                        long defaultTimeout = "WAIT_SMS".equalsIgnoreCase(action) ? 120_000L : 15_000L;
                        return UssdStepDto.builder()
                            .order(rs.getInt("step_order"))
                            .action(action)
                            .value(rs.getString("expression"))
                            .expectedPattern(rs.getString("expected_pattern"))
                            .variableName(rs.getString("extract_var"))
                            .timeoutMs(rs.getObject("wait_millis") != null ? rs.getLong("wait_millis") : defaultTimeout)
                            .build();
                    },
                    operator,
                    type
            );
            if (depth < 2) {
                for (UssdStepDto step : steps) {
                    if (!"RUN_TEMPLATE".equalsIgnoreCase(step.getAction())) {
                        continue;
                    }
                    String target = step.getValue() != null ? step.getValue().trim() : "";
                    if (target.isBlank() || target.equalsIgnoreCase(type)) {
                        continue;
                    }
                    step.setNestedSteps(loadUssdSteps(operator, target, depth + 1));
                }
            }
            return steps;
        } catch (Exception ex) {
            log.warn("Could not load USSD steps for {}/{}: {}", operator, type, ex.getMessage());
            return List.of();
        }
    }

    private List<BalancePatternDto> loadBalancePatterns(String operator) {
        if (operator == null || operator.isBlank()) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    """
                    SELECT p.field_type, p.regex_pattern, p.priority
                    FROM operator_balance_patterns p
                    JOIN operators o ON o.id = p.operator_id
                    WHERE UPPER(o.code) = UPPER(?)
                      AND p.active = TRUE
                    ORDER BY p.field_type ASC, p.priority ASC, p.id ASC
                    """,
                    (rs, rowNum) -> BalancePatternDto.builder()
                            .fieldType(rs.getString("field_type"))
                            .regexPattern(rs.getString("regex_pattern"))
                            .priority(rs.getInt("priority"))
                            .build(),
                    operator.trim()
            );
        } catch (Exception ex) {
            log.warn("Could not load balance patterns for {}: {}", operator, ex.getMessage());
            return List.of();
        }
    }

    public void enqueueFromSmsJob(Map<String, Object> job) {
        if (job == null) {
            return;
        }
        String recipient = job.get("recipient") != null ? job.get("recipient").toString() : null;
        String content = job.get("content") != null ? job.get("content").toString() : null;
        if (recipient == null || recipient.isBlank() || content == null || content.isBlank()) {
            log.warn("Ignoring SMS job without recipient/content: {}", job);
            return;
        }

        Long gatewayId = resolveOnlineGatewayId(null);
        if (gatewayId == null && job.get("gatewayId") != null && !job.get("gatewayId").toString().isBlank()) {
            gatewayId = Long.valueOf(job.get("gatewayId").toString());
        }
        if (gatewayId == null) {
            log.warn("No ONLINE gateway for SMS job smsId={}", job.get("smsId"));
            return;
        }

        String smsId = job.get("smsId") != null ? job.get("smsId").toString() : UUID.randomUUID().toString();
        GatewayTask task = GatewayTask.builder()
                .id("sms-" + smsId)
                .type("SMS")
                .phone(recipient)
                .smsTo(recipient)
                .smsBody(content)
                .priority(5)
                .timeoutSeconds(60)
                .build();
        enqueue(gatewayId, task);

        try {
            jdbcTemplate.update(
                    "UPDATE sms SET gateway_id = ?, status = 'SENDING', updated_at = NOW() WHERE id = ?",
                    gatewayId,
                    Long.valueOf(smsId)
            );
        } catch (Exception ex) {
            log.debug("Could not mark SMS {} as SENDING: {}", smsId, ex.getMessage());
        }
    }

    private Long resolveOnlineGatewayId(String operator) {
        try {
            if (operator != null && !operator.isBlank()) {
                Long matched = jdbcTemplate.query(
                        """
                        SELECT id FROM gateways
                        WHERE status = 'ONLINE'
                          AND last_heartbeat_at > NOW() - INTERVAL '2 minutes'
                          AND UPPER(operator) = UPPER(?)
                        ORDER BY last_heartbeat_at DESC NULLS LAST
                        LIMIT 1
                        """,
                        rs -> rs.next() ? rs.getLong(1) : null,
                        operator.trim()
                );
                if (matched != null) {
                    return matched;
                }
                log.warn("No ONLINE gateway with recent heartbeat for operator {}", operator);
                return null;
            }
            return jdbcTemplate.query(
                    """
                    SELECT id FROM gateways
                    WHERE status = 'ONLINE'
                      AND last_heartbeat_at > NOW() - INTERVAL '2 minutes'
                    ORDER BY last_heartbeat_at DESC NULLS LAST
                    LIMIT 1
                    """,
                    rs -> rs.next() ? rs.getLong(1) : null
            );
        } catch (Exception ex) {
            log.warn("Could not resolve ONLINE gateway: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isDuplicate(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        if (dispatchedTaskIds.contains(taskId)) {
            return true;
        }
        for (ConcurrentLinkedQueue<GatewayTask> queue : queues.values()) {
            for (GatewayTask existing : queue) {
                if (taskId.equals(existing.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<GatewayTask> poll(Long gatewayId, int limit) {
        ConcurrentLinkedQueue<GatewayTask> queue = queues.get(gatewayId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        int max = Math.max(1, Math.min(limit, 20));
        List<GatewayTask> out = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            GatewayTask task = queue.poll();
            if (task == null) {
                break;
            }
            out.add(task);
        }
        return out;
    }

    public int pendingCount(Long gatewayId) {
        ConcurrentLinkedQueue<GatewayTask> queue = queues.get(gatewayId);
        return queue == null ? 0 : queue.size();
    }

    public int requeueSendingSms() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT id, recipient, content, gateway_id
                    FROM sms
                    WHERE status = 'SENDING'
                    ORDER BY id ASC
                    """
            );
        } catch (Exception ex) {
            log.warn("Could not load SENDING sms: {}", ex.getMessage());
            return 0;
        }
        int count = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("smsId", row.get("id"));
            job.put("recipient", row.get("recipient"));
            job.put("content", row.get("content"));
            enqueueFromSmsJob(job);
            count++;
        }
        return count;
    }

    public void acceptResult(Long gatewayId, String taskId, TaskResultRequest result) {
        log.info("Task result gateway={} task={} status={}",
                gatewayId, taskId, result != null ? result.getStatus() : null);
        if (result == null) {
            return;
        }
        String hint = taskId != null ? taskId : result.getTaskId();
        if (hint != null && hint.startsWith("sms-")) {
            updateSmsFromResult(hint, result);
            return;
        }
        Long txId = parseTxId(hint);
        if (txId == null && result.getTaskId() != null) {
            txId = parseTxId(result.getTaskId());
        }
        if (txId == null) {
            return;
        }
        String status;
        if (result.getStatus() != null && "SUCCESS".equalsIgnoreCase(result.getStatus())) {
            status = "SUCCESS";
        } else if (result.getStatus() != null && "TIMEOUT".equalsIgnoreCase(result.getStatus())) {
            status = "TIMEOUT";
        } else if (result.getStatus() != null && "WAITING_SMS_CONFIRMATION".equalsIgnoreCase(result.getStatus())) {
            // Plus d'attente SMS : on tranche via le solde (ou FAILED si pas de solde)
            status = "FAILED";
        } else {
            status = "FAILED";
        }
        String confirmText = resolveConfirmText(result);
        try {
            BigDecimal balanceBefore = parseDecimal(extracted(result, "gateway_balance_before"));
            BigDecimal balanceAfter = parseDecimal(extracted(result, "gateway_balance_after"));
            BigDecimal balanceDelta = parseDecimal(extracted(result, "gateway_balance_delta"));
            if (balanceDelta == null && balanceBefore != null && balanceAfter != null) {
                balanceDelta = balanceAfter.subtract(balanceBefore);
            }

            Map<String, Object> txRow = loadTxRow(txId);
            String txType = txRow.get("type") != null ? txRow.get("type").toString() : null;
            BigDecimal txAmount = txRow.get("amount") != null
                    ? new BigDecimal(txRow.get("amount").toString()) : null;
            Boolean balanceConfirmed = balanceMatchesTransaction(balanceBefore, balanceAfter, txType, txAmount);

            // Source de vérité unique : delta solde gateway (pas de confirmation SMS)
            String confirmSource = "BALANCE";
            if (Boolean.TRUE.equals(balanceConfirmed)) {
                status = "SUCCESS";
            } else if (Boolean.FALSE.equals(balanceConfirmed)) {
                status = "FAILED";
            } else if (balanceBefore != null && balanceAfter == null
                    && "SUCCESS".equalsIgnoreCase(result.getStatus())) {
                // Solde avant OK mais après illisible → ne pas confirmer
                status = "FAILED";
            }

            String smsBody = extracted(result, "confirmation_sms", "sms_body");
            if (isGatewayUiNoise(smsBody)) {
                smsBody = null;
            }
            // SMS éventuel stocké pour audit uniquement — ne tranche jamais le statut
            if (smsBody != null && extracted(result, "confirmation_source") != null
                    && extracted(result, "confirmation_source").toUpperCase(Locale.ROOT).contains("SMS")) {
                // ignorer confirmation_source SMS côté statut
                log.info("Ignoring SMS confirmation for tx {} — balance-only mode", txId);
            }

            String orangeId = extracted(result, "orange_transaction_id");
            BigDecimal parsedAmount = parseDecimal(extracted(result, "parsed_amount"));
            String parsedPhone = extracted(result, "parsed_phone");
            // Éviter « WHEN ? IS NOT NULL » (PG/JDBC → bad SQL grammar / type inconnu)
            java.sql.Timestamp receivedAt = smsBody != null ? new java.sql.Timestamp(System.currentTimeMillis()) : null;
            // Ne pas persister le journal UI accessibilité comme ussd_response
            String ussdForStore = isGatewayUiNoise(confirmText) ? null : confirmText;
            // Pas de réouverture via SMS tardif : seul le solde confirme
            boolean lateSmsReopen = false;

            int updated = jdbcTemplate.update(
                    """
                    UPDATE transactions
                    SET status = ?,
                        ussd_response = COALESCE(?, ussd_response),
                        confirmation_sms = COALESCE(?, confirmation_sms),
                        confirmation_source = COALESCE(?, confirmation_source),
                        orange_transaction_id = COALESCE(?, orange_transaction_id),
                        parsed_amount = COALESCE(?, parsed_amount),
                        parsed_phone = COALESCE(?, parsed_phone),
                        confirmation_received_at = COALESCE(?, confirmation_received_at),
                        gateway_balance_before = COALESCE(?, gateway_balance_before),
                        gateway_balance_after = COALESCE(?, gateway_balance_after),
                        gateway_balance_delta = COALESCE(?, gateway_balance_delta),
                        balance_confirmed = COALESCE(?, balance_confirmed),
                        balance_confirmation_source = CASE
                            WHEN ? = 'BALANCE' THEN 'BALANCE'
                            ELSE balance_confirmation_source
                        END,
                        updated_at = NOW()
                    WHERE id = ?
                      AND (
                        status IN ('ASSIGNED', 'PROCESSING', 'WAITING_SMS_CONFIRMATION', 'QUEUED')
                        OR (? AND status = 'TIMEOUT')
                      )
                      AND status NOT IN ('SUCCESS', 'FAILED', 'CANCELLED')
                    """,
                    status,
                    ussdForStore,
                    smsBody,
                    confirmSource,
                    orangeId,
                    parsedAmount,
                    parsedPhone,
                    receivedAt,
                    balanceBefore,
                    balanceAfter,
                    balanceDelta,
                    balanceConfirmed,
                    confirmSource,
                    txId,
                    lateSmsReopen
            );
            if (updated > 0 && ("SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status))) {
                if ("SUCCESS".equals(status)) {
                    applyDistributorUvOnSuccess(txId);
                }
                notifyDistributorOnTerminalStatus(txId, status, confirmText);
            }
        } catch (Exception ex) {
            log.warn("Failed to update transaction {} from task result: {}", txId, ex.getMessage(), ex);
            // Fallback minimal : au moins terminer le statut (évite TX bloquée en ASSIGNED)
            try {
                String ussdFallback = isGatewayUiNoise(confirmText) ? null : confirmText;
                boolean lateFallback = ("SUCCESS".equals(status) || "FAILED".equals(status))
                        && extracted(result, "confirmation_sms", "sms_body") != null;
                int fallback = jdbcTemplate.update(
                        """
                        UPDATE transactions
                        SET status = ?,
                            ussd_response = COALESCE(?, ussd_response),
                            updated_at = NOW()
                        WHERE id = ?
                          AND (
                            status IN ('ASSIGNED', 'PROCESSING', 'WAITING_SMS_CONFIRMATION', 'QUEUED')
                            OR (? AND status = 'TIMEOUT')
                          )
                          AND status NOT IN ('SUCCESS', 'FAILED', 'CANCELLED')
                        """,
                        status,
                        ussdFallback,
                        txId,
                        lateFallback
                );
                if (fallback > 0 && ("SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status))) {
                    if ("SUCCESS".equals(status)) {
                        applyDistributorUvOnSuccess(txId);
                    }
                    notifyDistributorOnTerminalStatus(txId, status, confirmText);
                }
            } catch (Exception fallbackEx) {
                log.error("Fallback status update failed for tx {}: {}", txId, fallbackEx.getMessage());
            }
        }
    }

    /**
     * Expire les TX en attente SMS trop longtemps (retard opérateur dépassé).
     * Fenêtre alignée sur le soft-wait mobile (~15 min).
     */
    @Scheduled(fixedDelayString = "${osgateway.sms-confirm.expire-poll-ms:60000}")
    public void expireStaleSmsConfirmations() {
        try {
            int expireMinutes = Math.max(3, smsConfirmExpireMinutes);
            List<Map<String, Object>> stale = jdbcTemplate.queryForList(
                    """
                    SELECT id FROM transactions
                    WHERE status = 'WAITING_SMS_CONFIRMATION'
                      AND updated_at < NOW() - (? * INTERVAL '1 minute')
                    LIMIT 50
                    """,
                    expireMinutes
            );
            for (Map<String, Object> row : stale) {
                Long txId = ((Number) row.get("id")).longValue();
                Map<String, Object> txRow = loadTxRow(txId);
                BigDecimal balanceBefore = txRow.get("gateway_balance_before") != null
                        ? new BigDecimal(txRow.get("gateway_balance_before").toString()) : null;
                BigDecimal balanceAfter = txRow.get("gateway_balance_after") != null
                        ? new BigDecimal(txRow.get("gateway_balance_after").toString()) : null;
                String txType = txRow.get("type") != null ? txRow.get("type").toString() : null;
                BigDecimal txAmount = txRow.get("amount") != null
                        ? new BigDecimal(txRow.get("amount").toString()) : null;
                Boolean balanceConfirmed = balanceMatchesTransaction(balanceBefore, balanceAfter, txType, txAmount);

                String finalStatus;
                String confirmSource = null;
                if (Boolean.TRUE.equals(balanceConfirmed)) {
                    finalStatus = "SUCCESS";
                    confirmSource = "BALANCE";
                } else if (Boolean.FALSE.equals(balanceConfirmed)) {
                    finalStatus = "FAILED";
                    confirmSource = "BALANCE";
                } else {
                    finalStatus = "TIMEOUT";
                }

                int updated = jdbcTemplate.update(
                        """
                        UPDATE transactions
                        SET status = ?,
                            confirmation_source = COALESCE(?, confirmation_source),
                            balance_confirmation_source = CASE
                                WHEN ? = 'BALANCE' THEN 'BALANCE'
                                ELSE balance_confirmation_source
                            END,
                            balance_confirmed = COALESCE(?, balance_confirmed),
                            updated_at = NOW()
                        WHERE id = ?
                          AND status = 'WAITING_SMS_CONFIRMATION'
                        """,
                        finalStatus,
                        confirmSource,
                        confirmSource,
                        balanceConfirmed,
                        txId
                );
                if (updated > 0) {
                    if ("SUCCESS".equals(finalStatus)) {
                        applyDistributorUvOnSuccess(txId);
                    }
                    log.info("Expired WAITING_SMS_CONFIRMATION tx {} -> {} (balanceConfirmed={})",
                            txId, finalStatus, balanceConfirmed);
                    String detail = "SUCCESS".equals(finalStatus)
                            ? "Confirmée par solde gateway."
                            : "FAILED".equals(finalStatus)
                            ? "Infirmée par solde gateway."
                            : "Confirmation solde non disponible (délai dépassé).";
                    notifyDistributorOnTerminalStatus(txId, finalStatus, detail);
                }
            }
        } catch (Exception ex) {
            log.warn("expireStaleSmsConfirmations failed: {}", ex.getMessage());
        }
    }

    private static String extracted(TaskResultRequest result, String... keys) {
        if (result.getExtracted() == null) {
            return null;
        }
        for (String key : keys) {
            String v = result.getExtracted().get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Texte de confirmation : SMS opérateur corrélé, sinon réponse USSD / erreur.
     */
    private static String resolveConfirmText(TaskResultRequest result) {
        if (result.getExtracted() != null) {
            String sms = result.getExtracted().get("confirmation_sms");
            if (sms == null || sms.isBlank()) {
                sms = result.getExtracted().get("sms_body");
            }
            if (sms != null && !sms.isBlank()) {
                return sms.trim();
            }
            String ussdConfirm = result.getExtracted().get("ussd_confirm");
            if (ussdConfirm != null && !ussdConfirm.isBlank()) {
                return ussdConfirm.trim();
            }
        }
        if (result.getUssdResponse() != null && !result.getUssdResponse().isBlank()) {
            return result.getUssdResponse().trim();
        }
        if (result.getErrorMessage() != null && !result.getErrorMessage().isBlank()) {
            return result.getErrorMessage().trim();
        }
        return null;
    }

    /**
     * Notifie le distributeur (push + SMS générés) pour SUCCESS / FAILED / TIMEOUT.
     */
    private void notifyDistributorOnTerminalStatus(Long txId, String status, String confirmText) {
        if (!"SUCCESS".equals(status) && !"FAILED".equals(status) && !"TIMEOUT".equals(status)) {
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT t.reference, t.type, t.amount, t.beneficiary_phone, t.distributor_id,
                           t.ussd_response, t.confirmation_sms,
                           d.user_id AS user_id,
                           COALESCE(u.phone, d.phone) AS notify_phone
                    FROM transactions t
                    JOIN distributor_accounts d ON d.id = t.distributor_id
                    LEFT JOIN users u ON u.id = d.user_id
                    WHERE t.id = ?
                    """,
                    txId);
            if (rows.isEmpty()) {
                return;
            }
            Map<String, Object> row = rows.getFirst();
            Long userId = row.get("user_id") != null ? ((Number) row.get("user_id")).longValue() : null;
            String phone = row.get("notify_phone") != null ? row.get("notify_phone").toString().trim() : "";
            String reference = row.get("reference") != null ? row.get("reference").toString() : String.valueOf(txId);
            String type = row.get("type") != null ? row.get("type").toString() : "-";
            String beneficiary = row.get("beneficiary_phone") != null ? row.get("beneficiary_phone").toString() : "-";
            String amountLabel = "0";
            if (row.get("amount") != null) {
                amountLabel = new BigDecimal(row.get("amount").toString())
                        .setScale(0, RoundingMode.HALF_UP)
                        .toPlainString();
            }
            boolean success = "SUCCESS".equals(status);
            boolean timeout = "TIMEOUT".equals(status);
            String body = buildDistributorNotifyBody(
                    success,
                    timeout,
                    reference,
                    type,
                    amountLabel,
                    beneficiary);

            String notifType = success ? "TRANSACTION_SUCCESS"
                    : (timeout ? "TRANSACTION_TIMEOUT" : "TRANSACTION_FAILED");
            String title = success ? "Transaction réussie"
                    : (timeout ? "Transaction expirée" : "Transaction échouée");

            if (userId != null) {
                rabbitTemplate.convertAndSend(
                        QueueConstants.EXCHANGE,
                        QueueConstants.NOTIFICATION_ROUTING_KEY,
                        Map.of(
                                "type", notifType,
                                "title", title,
                                "message", body,
                                "userId", userId,
                                "severity", success ? "INFO" : "WARN"
                        ));
            }
            if (!phone.isBlank()) {
                Long smsId = jdbcTemplate.queryForObject(
                        """
                        INSERT INTO sms (recipient, content, status, priority, created_at, created_by)
                        VALUES (?, ?, 'QUEUED', 'NORMAL', NOW(), 'gateway-service')
                        RETURNING id
                        """,
                        Long.class,
                        phone,
                        body);
                if (smsId != null) {
                    jdbcTemplate.update(
                            """
                            INSERT INTO sms_history (sms_id, status, details, created_at)
                            VALUES (?, 'QUEUED', 'Transaction result notify', NOW())
                            """,
                            smsId);
                    rabbitTemplate.convertAndSend(
                            QueueConstants.EXCHANGE,
                            QueueConstants.SMS_ROUTING_KEY,
                            Map.of(
                                    "smsId", smsId,
                                    "recipient", phone,
                                    "content", body
                            ));
                }
            }
        } catch (Exception ex) {
            log.warn("Distributor notify failed for tx {}: {}", txId, ex.getMessage());
        }
    }

    /**
     * SMS / push distributeur : message généré par l'application uniquement
     * (pas de SMS opérateur ni texte USSD brut du gateway).
     */
    static String buildDistributorNotifyBody(
            boolean success,
            boolean timeout,
            String reference,
            String type,
            String amountLabel,
            String beneficiary) {
        String statusLabel = success ? "SUCCÈS" : (timeout ? "EXPIRÉE" : "ÉCHEC");
        StringBuilder sb = new StringBuilder();
        sb.append("OS Gateway · ").append(statusLabel).append('\n');
        sb.append("Ref: ").append(reference != null ? reference : "-").append('\n');
        sb.append("Type: ").append(type != null ? type : "-").append('\n');
        sb.append("Montant: ").append(amountLabel != null ? amountLabel : "0").append(" XOF\n");
        sb.append("Bénéficiaire: ").append(beneficiary != null ? beneficiary : "-");
        if (timeout) {
            sb.append("\nConfirmation non reçue (délai dépassé).");
        } else if (!success) {
            sb.append("\nTransaction échouée.");
        } else {
            sb.append("\nTransaction réussie.");
        }
        return sb.toString();
    }

    /** Compat tests / anciens appels (sans timeout). */
    static String buildDistributorNotifyBody(
            boolean success,
            String reference,
            String type,
            String amountLabel,
            String beneficiary,
            @SuppressWarnings("unused") String legacyDetail) {
        return buildDistributorNotifyBody(success, false, reference, type, amountLabel, beneficiary);
    }

    /** Compat tests / anciens appels (détail ignoré). */
    static String buildDistributorNotifyBody(
            boolean success,
            boolean timeout,
            String reference,
            String type,
            String amountLabel,
            String beneficiary,
            @SuppressWarnings("unused") String confirmationSms,
            @SuppressWarnings("unused") String ussdResponse) {
        return buildDistributorNotifyBody(success, timeout, reference, type, amountLabel, beneficiary);
    }

    /** Premier paragraphe (bloc ou ligne) d'un texte USSD multi-lignes. */
    static String firstParagraph(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().replace("\r\n", "\n").replace('\r', '\n');
        for (String block : t.split("\\n\\s*\\n")) {
            String p = block.trim();
            if (!p.isBlank()) {
                return p;
            }
        }
        for (String line : t.split("\n")) {
            String p = line.trim();
            if (!p.isBlank()) {
                return p;
            }
        }
        return null;
    }

    /** 1er paragraphe USSD pour notification distributeur (sans journal UI). */
    static String sanitizeUssdNotifyParagraph(String raw) {
        String para = firstParagraph(raw);
        if (para == null || para.isBlank() || isGatewayUiNoise(para)) {
            return null;
        }
        if (para.length() > 320) {
            para = para.substring(0, 317) + "...";
        }
        return para;
    }

    /** Journal / UI de l'app gateway capturé par accessibilité — à ne jamais stocker ni SMS. */
    static boolean isGatewayUiNoise(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("effacer le journal")
                || lower.contains("événement(s)")
                || lower.contains("evenement(s)")
                || lower.contains("poll tâches")
                || lower.contains("poll taches")
                || lower.contains("heartbeat")
                || lower.contains("boîte à outils")
                || lower.contains("boite a outils")
                || lower.contains("applock")
                || (lower.contains("journal") && (lower.contains("événement") || lower.contains("evenement")))
                || lower.contains("[ussd] dialog closed")
                || lower.contains("[transaction] waiting sms");
    }

    /**
     * Ne garde que les confirmations exploitables (SMS opérateur / texte USSD utile).
     * Écarte le journal de l'app gateway et autres écrans parasites.
     */
    static String sanitizeConfirmDetail(String raw) {
        if (raw == null || raw.isBlank() || isGatewayUiNoise(raw)) {
            return null;
        }
        String t = raw.trim().replace("\r\n", "\n").replace('\r', '\n');
        String lower = t.toLowerCase(Locale.ROOT);
        // Message USSD intermédiaire Orange (pas encore le SMS final) → ne pas le renvoyer tel quel
        if (lower.contains("le client doit confirmer")
                || lower.contains("entraint son code secret")
                || lower.contains("entrant son code secret")) {
            return null;
        }
        if (t.length() > 320) {
            t = t.substring(0, 317) + "...";
        }
        return t;
    }

    private Map<String, Object> loadTxRow(Long txId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT type, amount, confirmation_sms,
                           gateway_balance_before, gateway_balance_after, gateway_balance_delta
                    FROM transactions WHERE id = ?
                    """,
                    txId
            );
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * Mouvement UV distributeur au premier passage en SUCCESS.
     * Montant de la transaction seul. Retrait et achat UV créditent, les envois débitent.
     */
    private void applyDistributorUvOnSuccess(Long txId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT t.distributor_id, t.amount, t.type,
                           ot.balance_effect
                    FROM transactions t
                    LEFT JOIN operation_types ot ON UPPER(ot.code) = UPPER(t.type)
                    WHERE t.id = ?
                    """,
                    txId);
            if (rows.isEmpty()) {
                return;
            }
            Map<String, Object> row = rows.getFirst();
            if (row.get("distributor_id") == null) {
                return;
            }
            long distributorId = ((Number) row.get("distributor_id")).longValue();
            BigDecimal amount = row.get("amount") != null
                    ? new BigDecimal(row.get("amount").toString()) : BigDecimal.ZERO;
            if (amount.signum() <= 0) {
                return;
            }
            String typeCode = row.get("type") != null ? row.get("type").toString() : "";
            String configured = row.get("balance_effect") != null ? row.get("balance_effect").toString() : "";
            String effect = UvBalanceEffect.resolve(typeCode, configured);
            int updated = switch (effect) {
                case "CREDIT" -> jdbcTemplate.update(
                        """
                        UPDATE distributor_accounts
                        SET balance = balance + ?, updated_at = NOW()
                        WHERE id = ? AND active = TRUE
                        """,
                        amount, distributorId);
                case "DEBIT" -> jdbcTemplate.update(
                        """
                        UPDATE distributor_accounts
                        SET balance = balance - ?, updated_at = NOW()
                        WHERE id = ? AND active = TRUE
                        """,
                        amount, distributorId);
                default -> 0;
            };
            log.info("UV {} {} XOF distributor {} tx {} (rows={})",
                    effect, amount.toPlainString(), distributorId, txId, updated);
        } catch (Exception ex) {
            log.error("UV movement failed for tx {}: {}", txId, ex.getMessage());
        }
    }

    /** Delta attendu sur le solde SIM gateway (float Orange Money agent). */
    static BigDecimal expectedGatewayBalanceDelta(String txType, BigDecimal amount) {
        if (amount == null || txType == null || txType.isBlank()) {
            return null;
        }
        // Agent : dépôt client → float ↓ ; retrait client → float ↑
        return switch (txType.toUpperCase(Locale.ROOT)) {
            case "RETRAIT" -> amount;
            case "DEPOT", "TRANSFERT", "PAIEMENT", "ACHAT_CREDIT" -> amount.negate();
            default -> BigDecimal.ZERO;
        };
    }

    /** Vérifie que after - before correspond au montant et au sens de la TX (tolérance 5 XOF). */
    static Boolean balanceMatchesTransaction(
            BigDecimal before, BigDecimal after, String txType, BigDecimal txAmount) {
        if (before == null || after == null || txAmount == null || txType == null) {
            return null;
        }
        BigDecimal expected = expectedGatewayBalanceDelta(txType, txAmount);
        if (expected == null || expected.signum() == 0) {
            return null;
        }
        BigDecimal actual = after.subtract(before);
        BigDecimal tolerance = new BigDecimal("5");
        return actual.subtract(expected).abs().compareTo(tolerance) <= 0;
    }

    private void updateSmsFromResult(String hint, TaskResultRequest result) {
        try {
            Long smsId = Long.valueOf(hint.substring("sms-".length()));
            String status = result.getStatus() != null && "SUCCESS".equalsIgnoreCase(result.getStatus())
                    ? "SENT"
                    : "FAILED";
            jdbcTemplate.update(
                    """
                    UPDATE sms
                    SET status = ?, sent_at = CASE WHEN ? = 'SENT' THEN NOW() ELSE sent_at END,
                        updated_at = NOW()
                    WHERE id = ?
                    """,
                    status,
                    status,
                    smsId
            );
        } catch (Exception ex) {
            log.warn("Failed to update SMS from task {}: {}", hint, ex.getMessage());
        }
    }

    private Long parseTxId(String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        try {
            if (hint.startsWith("ussd-")) {
                return Long.valueOf(hint.substring("ussd-".length()));
            }
            return Long.valueOf(hint);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
