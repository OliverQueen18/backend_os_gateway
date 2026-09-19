package com.osgateway.user.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.common.messaging.QueueConstants;
import com.osgateway.user.api.dto.UserDtos.*;
import com.osgateway.user.domain.DistributorAccount;
import com.osgateway.user.domain.DistributorAttachment;
import com.osgateway.user.infrastructure.persistence.DistributorAccountRepository;
import com.osgateway.user.infrastructure.persistence.DistributorAttachmentRepository;
import com.osgateway.user.infrastructure.storage.DistributorFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistributorRegistrationService {

    private static final Set<String> DOC_TYPES = Set.of("RCCM", "NIF", "NINA", "ID_CARD", "OTHER");
    private static final Set<String> PAY_METHODS = Set.of("CASH", "BANK_TRANSFER", "MOBILE_MONEY", "OTHER");

    private final DistributorAccountRepository distributorRepository;
    private final DistributorAttachmentRepository attachmentRepository;
    private final DistributorFileStorage fileStorage;
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public BigDecimal configuredRegistrationFee() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT value FROM settings WHERE key = ?",
                    String.class,
                    "distributor.registration.fee");
            if (value == null || value.isBlank()) return new BigDecimal("25000");
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new BigDecimal("25000.00");
        }
    }

    public boolean requireFee() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT value FROM settings WHERE key = ?",
                    String.class,
                    "distributor.registration.require_fee");
            return value == null || Boolean.parseBoolean(value.trim());
        } catch (Exception e) {
            return true;
        }
    }

    @Transactional
    public void updateMyKyc(Long userId, RegistrationKycRequest request) {
        DistributorAccount account = requireByUser(userId);
        assertEditable(account);
        if (request.getRccm() != null) account.setRccm(trimToNull(request.getRccm()));
        if (request.getNif() != null) account.setNif(trimToNull(request.getNif()));
        if (request.getNina() != null) account.setNina(trimToNull(request.getNina()));
        if (request.getAddress() != null) account.setAddress(trimToNull(request.getAddress()));
        if (request.getLatitude() != null) account.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) account.setLongitude(request.getLongitude());
        if (request.getPhone() != null) account.setPhone(trimToNull(request.getPhone()));
        if (request.getName() != null && !request.getName().isBlank()) account.setName(request.getName().trim());
        if (account.getSubmittedAt() == null) account.setSubmittedAt(Instant.now());
        distributorRepository.save(account);
        notifyUser(account.getUserId(), "REGISTRATION_KYC", "Dossier mis à jour",
                "Vos informations d'inscription (RCCM/NIF/NINA) ont été enregistrées.", "INFO");
    }

    @Transactional
    public void applyKycFields(DistributorAccount account, String rccm, String nif, String nina) {
        account.setRccm(trimToNull(rccm));
        account.setNif(trimToNull(nif));
        account.setNina(trimToNull(nina));
    }

    @Transactional
    public AttachmentResponse uploadAttachment(Long userId, Long distributorId, String docType, MultipartFile file, boolean admin) {
        DistributorAccount account = admin
                ? distributorRepository.findById(distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"))
                : requireByUser(userId);
        if (!admin) assertEditable(account);
        String type = normalizeDocType(docType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Fichier requis");
        }
        try {
            var stored = fileStorage.store(account.getId(), file);
            DistributorAttachment att = DistributorAttachment.builder()
                    .distributorId(account.getId())
                    .docType(type)
                    .fileName(stored.fileName())
                    .contentType(stored.contentType())
                    .sizeBytes(stored.sizeBytes())
                    .storageKey(stored.storageKey())
                    .uploadedBy(userId)
                    .build();
            att = attachmentRepository.save(att);
            notifyUser(account.getUserId(), "REGISTRATION_DOC", "Pièce jointe reçue",
                    "Document " + type + " téléversé (" + stored.fileName() + ").", "INFO");
            return toAttachment(att);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Échec téléversement: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(Long distributorId) {
        if (!distributorRepository.existsById(distributorId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found");
        }
        return attachmentRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId).stream()
                .map(this::toAttachment)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listMyAttachments(Long userId) {
        return listAttachments(requireByUser(userId).getId());
    }

    @Transactional(readOnly = true)
    public Resource loadAttachment(Long distributorId, Long attachmentId) {
        DistributorAttachment att = attachmentRepository.findByIdAndDistributorId(attachmentId, distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attachment not found"));
        Path path = fileStorage.resolve(att.getStorageKey());
        if (!path.toFile().exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Fichier introuvable sur le stockage");
        }
        return new FileSystemResource(path);
    }

    @Transactional(readOnly = true)
    public DistributorAttachment getAttachmentMeta(Long distributorId, Long attachmentId) {
        return attachmentRepository.findByIdAndDistributorId(attachmentId, distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attachment not found"));
    }

    @Transactional
    public void payRegistrationFee(Long userId, RegistrationFeePaymentRequest request) {
        DistributorAccount account = requireByUser(userId);
        if ("APPROVED".equals(account.getRegistrationStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Compte déjà validé");
        }
        if ("REJECTED".equals(account.getRegistrationStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Compte rejeté — contactez l'administration");
        }
        if (account.isRegistrationFeePaid()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Frais déjà payés");
        }
        BigDecimal expected = account.getRegistrationFeeAmount() != null
                ? account.getRegistrationFeeAmount()
                : configuredRegistrationFee();
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(expected) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le montant doit être exactement " + expected + " XOF");
        }
        String method = request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!PAY_METHODS.contains(method)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mode de paiement invalide");
        }
        String reference = request.getReference() != null && !request.getReference().isBlank()
                ? request.getReference().trim()
                : "RF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        account.setRegistrationFeeAmount(expected);
        account.setRegistrationFeePaid(true);
        account.setRegistrationFeePaidAt(Instant.now());
        account.setRegistrationFeePaymentRef(reference);
        account.setRegistrationFeePaymentMethod(method);
        account.setRegistrationStatus("UNDER_REVIEW");
        if (account.getSubmittedAt() == null) account.setSubmittedAt(Instant.now());
        distributorRepository.save(account);

        jdbcTemplate.update(
                """
                INSERT INTO distributor_registration_payments
                    (distributor_id, amount, payment_method, reference, note, status, confirmed_by, confirmed_at, created_by)
                VALUES (?, ?, ?, ?, ?, 'CONFIRMED', ?, NOW(), ?)
                """,
                account.getId(),
                amount,
                method,
                reference,
                request.getNote(),
                "distributor",
                "distributor");

        notifyUser(account.getUserId(), "REGISTRATION_FEE_PAID", "Frais d'inscription reçus",
                "Paiement de " + amount + " XOF enregistré. Votre dossier est en cours d'examen.", "INFO");
        notifyAdmins("DISTRIBUTOR_REGISTRATION", "Nouveau dossier distributeur",
                "Le distributeur " + account.getCode() + " a payé les frais et attend validation.", "WARN");
    }

    @Transactional
    public void approve(Long distributorId, Long reviewerUserId) {
        DistributorAccount account = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        if ("APPROVED".equals(account.getRegistrationStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Compte déjà approuvé");
        }
        if (requireFee() && !account.isRegistrationFeePaid()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Les frais d'inscription doivent être payés avant validation");
        }
        account.setRegistrationStatus("APPROVED");
        account.setActive(true);
        account.setRejectionReason(null);
        account.setReviewedBy(reviewerUserId);
        account.setReviewedAt(Instant.now());
        distributorRepository.save(account);
        notifyUser(account.getUserId(), "REGISTRATION_APPROVED", "Compte validé",
                "Félicitations — votre compte distributeur est actif. Vous pouvez opérer.", "INFO");
    }

    @Transactional
    public void reject(Long distributorId, Long reviewerUserId, RejectDistributorRequest request) {
        DistributorAccount account = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        if ("APPROVED".equals(account.getRegistrationStatus()) && account.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Impossible de rejeter un compte déjà actif");
        }
        String reason = request.getReason().trim();
        account.setRegistrationStatus("REJECTED");
        account.setActive(false);
        account.setRejectionReason(reason);
        account.setReviewedBy(reviewerUserId);
        account.setReviewedAt(Instant.now());
        distributorRepository.save(account);
        notifyUser(account.getUserId(), "REGISTRATION_REJECTED", "Inscription rejetée",
                "Votre dossier a été rejeté : " + reason, "ERROR");
    }

    public int attachmentCount(Long distributorId) {
        return attachmentRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId).size();
    }

    private DistributorAccount requireByUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        return distributorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor account not found"));
    }

    private void assertEditable(DistributorAccount account) {
        if ("APPROVED".equals(account.getRegistrationStatus()) && account.isActive()) {
            // allow KYC update even after approve for data quality
            return;
        }
        if ("REJECTED".equals(account.getRegistrationStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Compte rejeté — modifications impossibles");
        }
    }

    private String normalizeDocType(String docType) {
        if (docType == null || docType.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "docType requis");
        }
        String type = docType.trim().toUpperCase(Locale.ROOT);
        if (!DOC_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "docType invalide");
        }
        return type;
    }

    private AttachmentResponse toAttachment(DistributorAttachment a) {
        return AttachmentResponse.builder()
                .id(a.getId())
                .distributorId(a.getDistributorId())
                .docType(a.getDocType())
                .fileName(a.getFileName())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public void notifyUser(Long userId, String type, String title, String message, String severity) {
        if (userId == null) return;
        sendNotification(Map.of(
                "type", type,
                "title", title,
                "message", message,
                "userId", userId,
                "severity", severity));
    }

    public void notifyAdmins(String type, String title, String message, String severity) {
        try {
            List<Long> adminIds = jdbcTemplate.queryForList(
                    """
                    SELECT u.id FROM users u
                    JOIN user_roles ur ON ur.user_id = u.id
                    JOIN roles r ON r.id = ur.role_id
                    WHERE r.name IN ('ADMIN','SUPERVISOR') AND u.enabled = TRUE
                    """,
                    Long.class);
            if (adminIds.isEmpty()) {
                sendNotification(Map.of(
                        "type", type, "title", title, "message", message, "severity", severity));
                return;
            }
            for (Long id : adminIds) {
                sendNotification(Map.of(
                        "type", type,
                        "title", title,
                        "message", message,
                        "userId", id,
                        "severity", severity));
            }
        } catch (Exception e) {
            sendNotification(Map.of(
                    "type", type, "title", title, "message", message, "severity", severity));
        }
    }

    private void sendNotification(Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(
                    QueueConstants.EXCHANGE,
                    QueueConstants.NOTIFICATION_ROUTING_KEY,
                    payload);
        } catch (Exception ignored) {
            // notification is best-effort
        }
    }
}
