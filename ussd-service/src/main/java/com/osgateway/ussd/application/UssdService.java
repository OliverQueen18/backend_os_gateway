package com.osgateway.ussd.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.ussd.domain.*;
import com.osgateway.ussd.infrastructure.persistence.*;
import com.osgateway.ussd.infrastructure.storage.OperatorLogoStorage;
import lombok.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UssdService {

    private final OperatorRepository operatorRepository;
    private final UssdTemplateRepository templateRepository;
    private final UssdStepRepository stepRepository;
    private final OperatorBalancePatternRepository balancePatternRepository;
    private final ScenarioEngine scenarioEngine;
    private final OperatorLogoStorage logoStorage;

    @Transactional(readOnly = true)
    public List<Operator> listOperators(Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return operatorRepository.findByActiveTrueOrderByNameAsc();
        }
        return operatorRepository.findAll();
    }

    @Transactional
    public Operator createOperator(Operator operator) {
        if (operator.getCode() != null) {
            operator.setCode(operator.getCode().trim().toUpperCase(Locale.ROOT));
        }
        if (operator.getLogoUrl() != null && operator.getLogoUrl().isBlank()) {
            operator.setLogoUrl(null);
        }
        operator.setCreatedBy("system");
        return operatorRepository.save(operator);
    }

    @Transactional
    public Operator updateOperator(Long id, Operator patch) {
        Operator op = operatorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));
        if (patch.getName() != null) op.setName(patch.getName());
        if (patch.getCode() != null && !patch.getCode().isBlank()) {
            op.setCode(patch.getCode().trim().toUpperCase(Locale.ROOT));
        }
        op.setActive(patch.isActive());
        if (patch.getLogoUrl() != null) {
            String url = patch.getLogoUrl().trim();
            op.setLogoUrl(url.isEmpty() ? null : url);
            // External URL replaces uploaded file reference for display
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (op.getLogoStorageKey() != null) {
                    logoStorage.deleteQuietly(op.getLogoStorageKey());
                    op.setLogoStorageKey(null);
                    op.setLogoContentType(null);
                }
            }
        }
        return operatorRepository.save(op);
    }

    @Transactional
    public Operator uploadLogo(Long id, MultipartFile file) {
        Operator op = operatorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Fichier logo requis");
        }
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ct.startsWith("image/")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le logo doit être une image");
        }
        try {
            if (op.getLogoStorageKey() != null) {
                logoStorage.deleteQuietly(op.getLogoStorageKey());
            }
            var stored = logoStorage.store(id, file);
            op.setLogoStorageKey(stored.storageKey());
            op.setLogoContentType(stored.contentType());
            op.setLogoUrl("/api/v1/ussd/operators/" + id + "/logo");
            return operatorRepository.save(op);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Échec upload logo: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public LogoFile loadLogo(Long id) {
        Operator op = operatorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));
        if (op.getLogoStorageKey() == null || op.getLogoStorageKey().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Aucun logo uploadé pour cet opérateur");
        }
        Path path = logoStorage.resolve(op.getLogoStorageKey());
        if (!path.toFile().exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Fichier logo introuvable");
        }
        String contentType = op.getLogoContentType() != null ? op.getLogoContentType() : "image/png";
        return new LogoFile(new FileSystemResource(path), contentType);
    }

    @Transactional
    public void deleteOperator(Long id) {
        Operator op = operatorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));

        List<UssdTemplate> templates = templateRepository.findByOperatorId(id);
        for (UssdTemplate template : templates) {
            stepRepository.deleteByTemplateId(template.getId());
            templateRepository.deleteById(template.getId());
        }

        if (op.getLogoStorageKey() != null) {
            logoStorage.deleteQuietly(op.getLogoStorageKey());
        }
        operatorRepository.delete(op);
    }

    @Transactional(readOnly = true)
    public List<TemplateView> listTemplates(Long operatorId) {
        List<UssdTemplate> templates = operatorId == null
                ? templateRepository.findAll()
                : templateRepository.findByOperatorId(operatorId);
        return templates.stream().map(this::toView).toList();
    }

    @Transactional
    public TemplateView createTemplate(UssdTemplate template, List<UssdStep> steps) {
        normalizeTransactionType(template);
        if (template.getOperatorId() == null || template.getTransactionType() == null
                || template.getTransactionType().isBlank()
                || template.getName() == null || template.getName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Opérateur, type et nom du modèle sont requis");
        }
        if (templateRepository.existsByOperatorIdAndTransactionTypeAndName(
                template.getOperatorId(), template.getTransactionType(), template.getName().trim())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Un modèle avec ce nom et ce type existe déjà pour cet opérateur");
        }
        template.setId(null);
        template.setName(template.getName().trim());
        template.setCreatedBy("system");
        UssdTemplate saved = templateRepository.save(template);
        replaceSteps(saved.getId(), steps);
        return toView(saved);
    }

    @Transactional
    public TemplateView updateTemplate(Long id, UssdTemplate patch, List<UssdStep> steps) {
        UssdTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        normalizeTransactionType(patch);
        if (patch.getName() != null) template.setName(patch.getName().trim());
        if (patch.getDescription() != null) template.setDescription(patch.getDescription());
        if (patch.getTransactionType() != null && !patch.getTransactionType().isBlank()) {
            template.setTransactionType(patch.getTransactionType());
        }
        if (patch.getOperatorId() != null) template.setOperatorId(patch.getOperatorId());
        template.setActive(patch.isActive());
        if (templateRepository.existsByOperatorIdAndTransactionTypeAndNameAndIdNot(
                template.getOperatorId(), template.getTransactionType(), template.getName(), id)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Un modèle avec ce nom et ce type existe déjà pour cet opérateur");
        }
        template = templateRepository.save(template);
        if (steps != null) {
            replaceSteps(id, steps);
        }
        return toView(template);
    }

    private static void normalizeTransactionType(UssdTemplate template) {
        if (template == null || template.getTransactionType() == null) {
            return;
        }
        template.setTransactionType(template.getTransactionType().trim());
    }

    private void replaceSteps(Long templateId, List<UssdStep> steps) {
        stepRepository.deleteByTemplateId(templateId);
        stepRepository.flush();
        if (steps == null || steps.isEmpty()) {
            return;
        }
        int order = 1;
        for (UssdStep step : steps) {
            if (step.getAction() == null || step.getAction().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chaque étape doit avoir une action");
            }
            String action = step.getAction().trim().toUpperCase(Locale.ROOT);
            try {
                UssdAction.valueOf(action);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Action USSD inconnue: " + step.getAction());
            }
            step.setId(null);
            step.setTemplateId(templateId);
            step.setAction(action);
            step.setStepOrder(order++);
            stepRepository.save(step);
        }
        stepRepository.flush();
    }

    @Transactional
    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        stepRepository.deleteByTemplateId(id);
        templateRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TemplateView getTemplate(Long id) {
        UssdTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        return toView(template);
    }

    @Transactional(readOnly = true)
    public TemplateView getByOperatorAndType(String operatorCode, String type) {
        Operator op = operatorRepository.findByCode(operatorCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));
        String normalized = type != null ? type.trim() : null;
        UssdTemplate template = templateRepository
                .findFirstByOperatorIdAndTransactionTypeAndActiveTrue(op.getId(), normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        return toView(template);
    }

    @Transactional(readOnly = true)
    public ScenarioEngine.ScenarioResult runScenario(Long templateId, Map<String, String> vars, Map<String, String> screens) {
        List<UssdStep> steps = stepRepository.findByTemplateIdOrderByStepOrderAsc(templateId);
        return scenarioEngine.execute(steps, vars, screens != null ? screens : Map.of());
    }

    private TemplateView toView(UssdTemplate template) {
        List<UssdStep> steps = stepRepository.findByTemplateIdOrderByStepOrderAsc(template.getId());
        return new TemplateView(
                template.getId(),
                template.getOperatorId(),
                template.getTransactionType(),
                template.getName(),
                template.getDescription(),
                template.isActive(),
                steps
        );
    }

    /**
     * Flat DTO so the console can reload a saved model without depending on
     * {@code { template, steps }} wrapping (which fell back to mock defaults).
     */
    @Data
    @AllArgsConstructor
    public static class TemplateView {
        private Long id;
        private Long operatorId;
        private String transactionType;
        private String name;
        private String description;
        private boolean active;
        private List<UssdStep> steps;
    }

    public record LogoFile(Resource resource, String contentType) {}

    @Transactional(readOnly = true)
    public List<OperatorBalancePattern> listBalancePatterns(Long operatorId) {
        operatorRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));
        return balancePatternRepository.findByOperatorIdOrderByFieldTypeAscPriorityAscIdAsc(operatorId);
    }

    @Transactional
    public List<OperatorBalancePattern> saveBalancePatterns(Long operatorId, List<BalancePatternInput> inputs) {
        operatorRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATOR_NOT_FOUND));
        balancePatternRepository.deleteByOperatorId(operatorId);
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<OperatorBalancePattern> saved = new java.util.ArrayList<>();
        for (BalancePatternInput in : inputs) {
            if (in == null || in.getRegexPattern() == null || in.getRegexPattern().isBlank()) {
                continue;
            }
            String fieldType = in.getFieldType() != null
                    ? in.getFieldType().trim().toUpperCase(Locale.ROOT) : "PRINCIPAL";
            if (!"PRINCIPAL".equals(fieldType) && !"BONUS_UV".equals(fieldType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "fieldType invalide: " + fieldType + " (PRINCIPAL ou BONUS_UV)");
            }
            OperatorBalancePattern row = OperatorBalancePattern.builder()
                    .operatorId(operatorId)
                    .fieldType(fieldType)
                    .regexPattern(in.getRegexPattern().trim())
                    .priority(in.getPriority() > 0 ? in.getPriority() : 10)
                    .active(in.isActive())
                    .description(in.getDescription())
                    .build();
            row.setCreatedBy("system");
            saved.add(balancePatternRepository.save(row));
        }
        return saved;
    }

    @Data
    public static class BalancePatternInput {
        private String fieldType;
        private String regexPattern;
        private int priority = 10;
        private boolean active = true;
        private String description;
    }
}
