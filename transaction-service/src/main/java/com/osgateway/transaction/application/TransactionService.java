package com.osgateway.transaction.application;

import com.osgateway.common.dto.PageResponse;
import com.osgateway.common.enums.Priority;
import com.osgateway.common.enums.TransactionStatus;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.transaction.domain.CancellationReason;
import com.osgateway.transaction.domain.Transaction;
import com.osgateway.transaction.domain.TransactionHistory;
import com.osgateway.transaction.infrastructure.persistence.CancellationReasonRepository;
import com.osgateway.transaction.infrastructure.persistence.TransactionHistoryRepository;
import com.osgateway.transaction.infrastructure.persistence.TransactionRepository;
import lombok.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionHistoryRepository historyRepository;
    private final CancellationReasonRepository cancellationReasonRepository;
    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Transaction create(CreateRequest request) {
        if (request.getType() == null || request.getType().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type is required");
        }
        String typeCode = request.getType().trim().toUpperCase(Locale.ROOT);
        String operatorCode = request.getOperator() != null ? request.getOperator().trim() : "";
        OperationTypeConfig config = withOperatorCommission(
                resolveOperationTypeConfig(typeCode), typeCode, operatorCode);

        Long distributorId = request.getDistributorId();
        if (distributorId == null && request.getUserId() != null) {
            distributorId = resolveDistributorIdByUser(request.getUserId());
            request.setDistributorId(distributorId);
        }
        if (distributorId != null) {
            verifyDistributorPin(distributorId, request.getPin());
        }

        Priority priority = request.getPriority() != null ? request.getPriority() : Priority.NORMAL;
        String phone = request.getBeneficiaryPhone() != null ? request.getBeneficiaryPhone().trim() : "";
        if (config.requiresPhone() && phone.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le numéro de téléphone du client est obligatoire pour ce type d'opération");
        }
        BigDecimal amount = request.getAmount();
        if (config.requiresAmount()) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Le montant est obligatoire et doit être supérieur à 0 pour ce type d'opération");
            }
        } else if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
        }
        CommissionSplit split = computeCommission(amount, config, typeCode, operatorCode);

        // Solde UV : vérif. indicative à la création ; débit uniquement à SUCCESS (montant hors commission)
        if (distributorId != null && "DEBIT".equals(config.balanceEffect())) {
            assertSufficientBalance(distributorId, amount);
        }

        Transaction tx = Transaction.builder()
                .reference("TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
                .userId(request.getUserId())
                .distributorId(distributorId)
                .operator(request.getOperator())
                .type(typeCode)
                .beneficiaryPhone(phone.isBlank() ? null : phone)
                .amount(amount)
                .commission(split.total())
                .adminCommission(split.admin())
                .distributorCommission(split.distributor())
                .operatorCommission(split.operator())
                .status(TransactionStatus.PENDING)
                .priority(priority)
                .build();
        tx.setCreatedBy(request.getUserId() != null ? request.getUserId().toString() : "system");
        tx = transactionRepository.save(tx);
        history(tx.getId(), "NONE", TransactionStatus.PENDING.name(), "Created");

        TransactionWorkflow.assertTransition(TransactionStatus.PENDING, TransactionStatus.QUEUED);
        tx.setStatus(TransactionStatus.QUEUED);
        tx = transactionRepository.save(tx);
        history(tx.getId(), TransactionStatus.PENDING.name(), TransactionStatus.QUEUED.name(), "Published to queue");

        rabbitTemplate.convertAndSend(QueueConstants.EXCHANGE, QueueConstants.TRANSACTION_ROUTING_KEY,
                Map.of(
                        "transactionId", tx.getId(),
                        "reference", tx.getReference(),
                        "operator", tx.getOperator(),
                        "type", tx.getType(),
                        "priority", priority.name()
                ),
                message -> {
                    message.getMessageProperties().setPriority(priority.getRabbitPriority());
                    return message;
                });
        // Pas de notification tant que la TX n'est pas SUCCESS / FAILED
        return tx;
    }

    /**
     * Best-effort SMS + in-app/push notification to the distributor.
     * Uniquement pour les statuts terminaux SUCCESS / FAILED / CANCELLED.
     */
    private void notifyDistributor(Transaction tx) {
        if (tx.getDistributorId() == null || tx.getStatus() == null) {
            return;
        }
        if (tx.getStatus() != TransactionStatus.SUCCESS
                && tx.getStatus() != TransactionStatus.FAILED
                && tx.getStatus() != TransactionStatus.CANCELLED
                && tx.getStatus() != TransactionStatus.TIMEOUT) {
            return;
        }
        try {
            Map<String, Object> dist = jdbcTemplate.queryForMap(
                    """
                    SELECT d.phone AS phone, d.user_id AS user_id, d.code AS code,
                           COALESCE(u.phone, d.phone) AS notify_phone
                    FROM distributor_accounts d
                    LEFT JOIN users u ON u.id = d.user_id
                    WHERE d.id = ?
                    """,
                    tx.getDistributorId());
            String phone = dist.get("notify_phone") != null ? dist.get("notify_phone").toString().trim() : "";
            Long userId = dist.get("user_id") != null ? ((Number) dist.get("user_id")).longValue() : null;
            String amountLabel = tx.getAmount() != null
                    ? tx.getAmount().setScale(0, RoundingMode.HALF_UP).toPlainString()
                    : "0";
            boolean success = tx.getStatus() == TransactionStatus.SUCCESS;
            boolean cancelled = tx.getStatus() == TransactionStatus.CANCELLED;
            boolean timeout = tx.getStatus() == TransactionStatus.TIMEOUT;
            String statusLabel = success ? "SUCCÈS" : (cancelled ? "ANNULÉE" : (timeout ? "EXPIRÉE" : "ÉCHEC"));
            StringBuilder sb = new StringBuilder();
            sb.append("OS Gateway · ").append(statusLabel).append('\n');
            sb.append("Ref: ").append(tx.getReference()).append('\n');
            sb.append("Type: ").append(tx.getType()).append('\n');
            sb.append("Montant: ").append(amountLabel).append(" XOF\n");
            sb.append("Bénéficiaire: ").append(tx.getBeneficiaryPhone() != null ? tx.getBeneficiaryPhone() : "-");
            if (timeout) {
                sb.append("\nConfirmation non reçue (délai dépassé).");
            } else if (cancelled) {
                sb.append("\nTransaction annulée.");
            } else if (!success) {
                sb.append("\nTransaction échouée.");
            } else {
                sb.append("\nTransaction réussie.");
            }
            String body = sb.toString();

            if (userId != null) {
                rabbitTemplate.convertAndSend(
                        QueueConstants.EXCHANGE,
                        QueueConstants.NOTIFICATION_ROUTING_KEY,
                        Map.of(
                                "type", success ? "TRANSACTION_SUCCESS"
                                        : (cancelled ? "TRANSACTION_CANCELLED"
                                        : (timeout ? "TRANSACTION_TIMEOUT" : "TRANSACTION_FAILED")),
                                "title", success ? "Transaction réussie"
                                        : (cancelled ? "Transaction annulée"
                                        : (timeout ? "Transaction expirée" : "Transaction échouée")),
                                "message", body,
                                "userId", userId,
                                "severity", success ? "INFO" : "WARN"
                        ));
            }

            if (!phone.isBlank()) {
                Long smsId = jdbcTemplate.queryForObject(
                        """
                        INSERT INTO sms (recipient, content, status, priority, created_at, created_by)
                        VALUES (?, ?, 'QUEUED', 'NORMAL', NOW(), 'transaction-service')
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
            // Notifications must never fail the transaction
            org.slf4j.LoggerFactory.getLogger(TransactionService.class)
                    .warn("Distributor notify failed for tx {}: {}", tx.getId(), ex.getMessage());
        }
    }

    /** Écarte journal UI / bruit accessibilité du SMS distributeur. */
    static String sanitizeDistributorDetail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().replace("\r\n", "\n").replace('\r', '\n');
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("effacer le journal")
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
                || lower.contains("[transaction] waiting sms")
                || lower.contains("le client doit confirmer")
                || lower.contains("entrant son code secret")
                || lower.contains("entraint son code secret")) {
            return null;
        }
        if (t.length() > 320) {
            t = t.substring(0, 317) + "...";
        }
        return t;
    }

    /** Premier paragraphe d'une réponse USSD (bloc ou première ligne non vide). */
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

    /** 1er paragraphe USSD pour SMS distributeur (hors journal UI). */
    static String sanitizeUssdNotifyParagraph(String raw) {
        String para = firstParagraph(raw);
        if (para == null || para.isBlank()) {
            return null;
        }
        String lower = para.toLowerCase(Locale.ROOT);
        if (lower.contains("effacer le journal")
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
                || lower.contains("[transaction] waiting sms")) {
            return null;
        }
        if (para.length() > 320) {
            para = para.substring(0, 317) + "...";
        }
        return para;
    }

    @Transactional
    public Transaction updateStatus(Long id, TransactionStatus status, String note, Long gatewayId,
                                    String ussdResponse, String screenshotUrl, Long durationMs) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        TransactionWorkflow.assertTransition(tx.getStatus(), status);
        String from = tx.getStatus().name();

        // Solde UV : mouvement uniquement sur SUCCESS (montant TX, sans commission)
        if (status == TransactionStatus.SUCCESS
                && tx.getDistributorId() != null
                && !"SUCCESS".equals(from)) {
            applyBalanceOnSuccess(tx);
        }

        tx.setStatus(status);
        if (gatewayId != null) tx.setGatewayId(gatewayId);
        if (ussdResponse != null) tx.setUssdResponse(ussdResponse);
        if (screenshotUrl != null) tx.setScreenshotUrl(screenshotUrl);
        if (durationMs != null) tx.setDurationMs(durationMs);
        tx = transactionRepository.save(tx);
        history(id, from, status.name(), note);
        if (status == TransactionStatus.SUCCESS
                || status == TransactionStatus.FAILED
                || status == TransactionStatus.CANCELLED
                || status == TransactionStatus.TIMEOUT) {
            notifyDistributor(tx);
        }
        return tx;
    }

    /**
     * Annulation console : motif obligatoire, commissions mises à 0 (aucun mouvement de solde UV).
     */
    @Transactional
    public Transaction cancel(Long id, Long cancellationReasonId, String note) {
        if (cancellationReasonId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le motif d'annulation est obligatoire");
        }
        CancellationReason reason = cancellationReasonRepository.findById(cancellationReasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Motif d'annulation introuvable"));
        if (!reason.isActive()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ce motif d'annulation est inactif");
        }

        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        if (!TransactionWorkflow.isCancellableStatus(tx.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_TRANSACTION_STATUS,
                    "La transaction ne peut plus être annulée (statut " + tx.getStatus() + ")");
        }
        if (!isOperationTypeCancellable(tx.getType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Ce type d'opération n'est pas annulable");
        }

        TransactionWorkflow.assertTransition(tx.getStatus(), TransactionStatus.CANCELLED);
        String from = tx.getStatus().name();
        BigDecimal commissionBefore = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;

        // Annule la commission sur la transaction (plus comptabilisée) — solde UV inchangé (jamais débité)
        tx.setCommission(BigDecimal.ZERO);
        tx.setAdminCommission(BigDecimal.ZERO);
        tx.setDistributorCommission(BigDecimal.ZERO);
        tx.setCancellationReasonId(reason.getId());
        String freeNote = note != null && !note.isBlank() ? note.trim() : null;
        tx.setCancellationNote(freeNote);
        tx.setStatus(TransactionStatus.CANCELLED);
        tx = transactionRepository.save(tx);

        String historyNote = "Annulée — " + reason.getLabel()
                + (freeNote != null ? (" : " + freeNote) : "")
                + " (commission " + commissionBefore.toPlainString() + " XOF annulée)";
        history(id, from, TransactionStatus.CANCELLED.name(), historyNote);
        notifyDistributor(tx);
        return tx;
    }

    private boolean isOperationTypeCancellable(String typeCode) {
        try {
            Boolean cancellable = jdbcTemplate.query(
                    """
                    SELECT COALESCE(cancellable, TRUE) FROM operation_types
                    WHERE UPPER(code) = ? AND active = TRUE
                    """,
                    rs -> rs.next() ? rs.getBoolean(1) : Boolean.TRUE,
                    typeCode != null ? typeCode.trim().toUpperCase(Locale.ROOT) : "");
            return cancellable == null || cancellable;
        } catch (Exception ex) {
            return true;
        }
    }

    @Transactional(readOnly = true)
    public Transaction get(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<Transaction> history(
            TransactionStatus status,
            String operator,
            String type,
            Long userId,
            Long distributorId,
            Instant from,
            Instant to,
            int page,
            int size) {
        // Never pass null String into JPQL string ops — Postgres binds it as bytea and fails.
        // Always pass concrete Instant bounds (wide sentinels) so PG can type the parameters.
        String typeFilter = type != null && !type.isBlank()
                ? type.trim().toUpperCase(Locale.ROOT) : "";
        String operatorFilter = operator != null && !operator.isBlank() ? operator.trim() : "";
        Instant fromFilter = from != null ? from : Instant.EPOCH;
        Instant toFilter = to != null ? to : Instant.parse("9999-12-31T23:59:59Z");
        Page<Transaction> result = transactionRepository.search(
                status, operatorFilter, typeFilter, userId, distributorId, fromFilter, toFilter,
                PageRequest.of(page, size));
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<TransactionHistory> timeline(Long id) {
        return historyRepository.findByTransactionIdOrderByCreatedAtAsc(id);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findPending() {
        return transactionRepository.findByStatus(TransactionStatus.QUEUED);
    }

    private OperationTypeConfig resolveOperationTypeConfig(String typeCode) {
        List<OperationTypeConfig> configs = jdbcTemplate.query(
                """
                SELECT balance_effect, commission_mode, commission_value,
                       admin_share_percent, distributor_share_percent,
                       COALESCE(requires_phone, TRUE) AS requires_phone,
                       COALESCE(requires_amount, TRUE) AS requires_amount
                FROM operation_types
                WHERE UPPER(code) = ? AND active = TRUE
                """,
                (rs, rowNum) -> new OperationTypeConfig(
                        rs.getString("balance_effect"),
                        rs.getString("commission_mode"),
                        rs.getBigDecimal("commission_value"),
                        rs.getBigDecimal("admin_share_percent"),
                        rs.getBigDecimal("distributor_share_percent"),
                        rs.getBoolean("requires_phone"),
                        rs.getBoolean("requires_amount")
                ),
                typeCode);
        if (configs.isEmpty()) {
            String balanceEffect = switch (typeCode) {
                case "SOLDE" -> "NONE";
                case "ACHAT_UV" -> "CREDIT";
                default -> "DEBIT";
            };
            boolean requiresPhone = !"SOLDE".equals(typeCode) && !"ACHAT_UV".equals(typeCode);
            boolean requiresAmount = !"SOLDE".equals(typeCode);
            return new OperationTypeConfig(
                    balanceEffect,
                    "PERCENT",
                    new BigDecimal("1.50"),
                    new BigDecimal("40.00"),
                    new BigDecimal("60.00"),
                    requiresPhone,
                    requiresAmount);
        }
        return configs.getFirst();
    }

    /** Surcharge mode, valeur et parts si une règle existe pour cet opérateur. */
    private OperationTypeConfig withOperatorCommission(
            OperationTypeConfig base, String typeCode, String operatorCode) {
        if (operatorCode == null || operatorCode.isBlank()) {
            return base;
        }
        try {
            List<OperationTypeConfig> rows = jdbcTemplate.query(
                    """
                    SELECT c.commission_mode, c.commission_value,
                           c.admin_share_percent, c.distributor_share_percent
                    FROM operation_operator_commissions c
                    JOIN operation_types ot ON ot.id = c.operation_type_id
                    JOIN operators o ON o.id = c.operator_id
                    WHERE UPPER(ot.code) = ? AND UPPER(o.code) = ?
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new OperationTypeConfig(
                            base.balanceEffect(),
                            rs.getString("commission_mode"),
                            rs.getBigDecimal("commission_value"),
                            rs.getBigDecimal("admin_share_percent"),
                            rs.getBigDecimal("distributor_share_percent"),
                            base.requiresPhone(),
                            base.requiresAmount()),
                    typeCode,
                    operatorCode.trim().toUpperCase(Locale.ROOT));
            return rows.isEmpty() ? base : rows.getFirst();
        } catch (org.springframework.dao.DataAccessException ex) {
            return base;
        }
    }

    /**
     * Barème actif (opérateur + type + palier + dates) en priorité.
     * Sinon, commission du type, éventuellement surchargée par operation_operator_commissions.
     */
    private CommissionSplit computeCommission(
            BigDecimal amount, OperationTypeConfig config, String typeCode, String operatorCode) {
        BigDecimal base = amount != null ? amount : BigDecimal.ZERO;
        CommissionCalculator.Rule rule = findCommissionRule(typeCode, operatorCode, base);
        if (rule != null) {
            CommissionCalculator.Split split = CommissionCalculator.apply(base, rule);
            return new CommissionSplit(split.total(), split.admin(), split.distributor(), split.operatorShare());
        }
        String mode = config.commissionMode() != null
                ? config.commissionMode().trim().toUpperCase(Locale.ROOT)
                : "PERCENT";
        BigDecimal value = config.commissionValue() != null ? config.commissionValue() : new BigDecimal("1.50");
        BigDecimal total;
        if ("FIXED".equals(mode)) {
            total = value.setScale(2, RoundingMode.HALF_UP);
        } else {
            total = base.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal adminShare = config.adminSharePercent() != null
                ? config.adminSharePercent()
                : new BigDecimal("40.00");
        BigDecimal admin = total.multiply(adminShare)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal distributor = total.subtract(admin).setScale(2, RoundingMode.HALF_UP);
        return new CommissionSplit(total, admin, distributor, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private CommissionCalculator.Rule findCommissionRule(String typeCode, String operatorCode, BigDecimal amount) {
        if (typeCode == null || typeCode.isBlank() || operatorCode == null || operatorCode.isBlank()) {
            return null;
        }
        try {
            List<CommissionCalculator.Rule> rows = jdbcTemplate.query(
                    """
                    SELECT r.calculation_mode, r.rate_percent, r.commission_min, r.commission_max,
                           r.distributor_rate, r.admin_rate, r.operator_rate
                    FROM commission_rules r
                    JOIN operation_types ot ON ot.id = r.operation_type_id
                    JOIN operators o ON o.id = r.operator_id
                    WHERE UPPER(ot.code) = ?
                      AND UPPER(o.code) = ?
                      AND r.active = TRUE
                      AND ? >= r.amount_min
                      AND (r.amount_max IS NULL OR ? <= r.amount_max)
                      AND (r.valid_from IS NULL OR r.valid_from <= CURRENT_DATE)
                      AND (r.valid_to IS NULL OR r.valid_to >= CURRENT_DATE)
                    ORDER BY r.priority DESC,
                             (COALESCE(r.amount_max, 1000000000000) - r.amount_min) ASC,
                             r.id DESC
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new CommissionCalculator.Rule(
                            rs.getString("calculation_mode"),
                            rs.getBigDecimal("rate_percent"),
                            rs.getBigDecimal("commission_min"),
                            rs.getBigDecimal("commission_max"),
                            rs.getBigDecimal("distributor_rate"),
                            rs.getBigDecimal("admin_rate"),
                            rs.getBigDecimal("operator_rate")),
                    typeCode.trim().toUpperCase(Locale.ROOT),
                    operatorCode.trim().toUpperCase(Locale.ROOT),
                    amount,
                    amount);
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    private Long resolveDistributorIdByUser(Long userId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM distributor_accounts WHERE user_id = ? AND active = TRUE ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                userId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void verifyDistributorPin(Long distributorId, String pin) {
        if (pin == null || pin.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le code PIN du distributeur est obligatoire");
        }
        String cleaned = pin.trim();
        if (!cleaned.matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le code PIN doit contenir 4 à 6 chiffres");
        }
        List<String> hashes = jdbcTemplate.query(
                "SELECT pin_hash FROM distributor_accounts WHERE id = ? AND active = TRUE",
                (rs, rowNum) -> rs.getString(1),
                distributorId);
        if (hashes.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Distributeur introuvable ou inactif");
        }
        String hash = hashes.getFirst();
        if (hash == null || hash.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Aucun PIN configuré pour ce distributeur — définissez-le dans Distributeurs");
        }
        if (!passwordEncoder.matches(cleaned, hash)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Code PIN incorrect");
        }
    }

    /** Débit/crédit UV à SUCCESS uniquement — montant de la TX, jamais la commission. */
    private void applyBalanceOnSuccess(Transaction tx) {
        String effect = resolveOperationTypeConfig(tx.getType()).balanceEffect();
        BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if ("DEBIT".equals(effect)) {
            debitDistributor(tx.getDistributorId(), amount);
        } else if ("CREDIT".equals(effect)) {
            creditDistributor(tx.getDistributorId(), amount);
        }
    }

    private void assertSufficientBalance(Long distributorId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<BigDecimal> balances = jdbcTemplate.query(
                "SELECT balance FROM distributor_accounts WHERE id = ? AND active = TRUE",
                (rs, rowNum) -> rs.getBigDecimal(1),
                distributorId);
        if (balances.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Distributeur introuvable ou inactif");
        }
        BigDecimal balance = balances.getFirst() != null ? balances.getFirst() : BigDecimal.ZERO;
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Solde UV insuffisant pour le distributeur " + distributorId);
        }
    }

    private void debitDistributor(Long distributorId, BigDecimal total) {
        int updated = jdbcTemplate.update(
                """
                UPDATE distributor_accounts
                SET balance = balance - ?, updated_at = NOW()
                WHERE id = ? AND active = TRUE AND balance >= ?
                """,
                total, distributorId, total);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Solde UV insuffisant pour le distributeur " + distributorId);
        }
    }

    private void creditDistributor(Long distributorId, BigDecimal amount) {
        jdbcTemplate.update(
                "UPDATE distributor_accounts SET balance = balance + ?, updated_at = NOW() WHERE id = ?",
                amount, distributorId);
    }

    private void history(Long txId, String from, String to, String note) {
        historyRepository.save(TransactionHistory.builder()
                .transactionId(txId).fromStatus(from).toStatus(to).note(note).createdAt(Instant.now()).build());
    }

    private record OperationTypeConfig(
            String balanceEffect,
            String commissionMode,
            BigDecimal commissionValue,
            BigDecimal adminSharePercent,
            BigDecimal distributorSharePercent,
            boolean requiresPhone,
            boolean requiresAmount) {}

    private record CommissionSplit(
            BigDecimal total, BigDecimal admin, BigDecimal distributor, BigDecimal operator) {}

    @Data
    public static class CreateRequest {
        private Long userId;
        private Long distributorId;
        /** PIN transaction du distributeur (4–6 chiffres). */
        private String pin;
        private String operator;
        private String type;
        private String beneficiaryPhone;
        private BigDecimal amount;
        private BigDecimal commission;
        private Priority priority;
    }
}
