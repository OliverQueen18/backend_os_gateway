package com.osgateway.transaction.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.transaction.domain.CancellationReason;
import com.osgateway.transaction.infrastructure.persistence.CancellationReasonRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CancellationReasonService {

    private final CancellationReasonRepository repository;

    @Transactional(readOnly = true)
    public List<CancellationReason> list(boolean activeOnly) {
        return activeOnly ? repository.findByActiveTrueOrderByLabelAsc() : repository.findAllByOrderByLabelAsc();
    }

    @Transactional(readOnly = true)
    public CancellationReason get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Motif d'annulation introuvable"));
    }

    @Transactional
    public CancellationReason create(UpsertRequest request) {
        String code = normalizeCode(request.getCode());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Code motif déjà utilisé: " + code);
        }
        if (request.getLabel() == null || request.getLabel().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le libellé du motif est obligatoire");
        }
        CancellationReason reason = CancellationReason.builder()
                .code(code)
                .label(request.getLabel().trim())
                .description(blankToNull(request.getDescription()))
                .active(request.getActive() == null || request.getActive())
                .build();
        return repository.save(reason);
    }

    @Transactional
    public CancellationReason update(Long id, UpsertRequest request) {
        CancellationReason reason = get(id);
        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = normalizeCode(request.getCode());
            repository.findByCodeIgnoreCase(code).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Code motif déjà utilisé: " + code);
                }
            });
            reason.setCode(code);
        }
        if (request.getLabel() != null && !request.getLabel().isBlank()) {
            reason.setLabel(request.getLabel().trim());
        }
        if (request.getDescription() != null) {
            reason.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getActive() != null) {
            reason.setActive(request.getActive());
        }
        return repository.save(reason);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Motif d'annulation introuvable");
        }
        repository.deleteById(id);
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le code du motif est obligatoire");
        }
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    @Data
    public static class UpsertRequest {
        private String code;
        private String label;
        private String description;
        private Boolean active;
    }
}
