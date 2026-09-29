package com.osgateway.gatewaydevice.application;

import com.osgateway.common.enums.GatewayStatus;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.gatewaydevice.api.dto.GatewayDtos.*;
import com.osgateway.gatewaydevice.domain.*;
import com.osgateway.gatewaydevice.infrastructure.persistence.*;
import com.osgateway.gatewaydevice.infrastructure.ws.GatewayWsPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GatewayService {

    private final GatewayRepository gatewayRepository;
    private final GatewayStatusRepository statusRepository;
    private final GatewayLogRepository logRepository;
    private final GatewaySelectionService selectionService;
    private final GatewayWsPublisher wsPublisher;
    private final GatewayTaskQueueService taskQueueService;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public List<GatewayResponse> list(String operator, GatewayStatus status) {
        return gatewayRepository.findFiltered(operator, status).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GatewayResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public GatewayResponse register(RegisterRequest request) {
        return gatewayRepository.findByDeviceId(request.getDeviceId())
                .map(existing -> {
                    if (request.getName() != null && !request.getName().isBlank()) {
                        existing.setName(request.getName());
                    }
                    if (request.getPhoneNumber() != null) {
                        existing.setPhoneNumber(request.getPhoneNumber());
                    }
                    if (request.getOperator() != null && !request.getOperator().isBlank()) {
                        existing.setOperator(request.getOperator());
                    }
                    applyUssdPin(existing, request.getUssdPin(), false);
                    GatewayDevice saved = gatewayRepository.save(existing);
                    log(saved.getId(), "REGISTER", "Gateway already registered — reused");
                    return toResponse(saved);
                })
                .orElseGet(() -> createNew(request));
    }

    private GatewayResponse createNew(RegisterRequest request) {
        if (request.getOperator() == null || request.getOperator().isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Opérateur requis pour un nouveau gateway — créez-le dans la console ou renseignez l'opérateur dans les réglages de l'app"
            );
        }
        GatewayDevice gateway = GatewayDevice.builder()
                .deviceId(request.getDeviceId())
                .name(request.getName())
                .operator(request.getOperator().trim().toUpperCase())
                .phoneNumber(request.getPhoneNumber())
                .status(GatewayStatus.OFFLINE)
                .loadScore(0)
                .batteryLevel(100)
                .networkStrength(0)
                .internetAvailable(false)
                .lastIdleAt(Instant.now())
                .apiKeyHash(request.getApiKey() != null ? encoder.encode(request.getApiKey()) : null)
                .ussdPin(normalizeUssdPin(request.getUssdPin(), false))
                .build();
        gateway.setCreatedBy("register");
        gateway = gatewayRepository.save(gateway);
        log(gateway.getId(), "REGISTER", "Gateway registered");
        wsPublisher.publish(toResponse(gateway));
        return toResponse(gateway);
    }

    @Transactional
    public GatewayResponse update(Long id, UpdateRequest request) {
        GatewayDevice gateway = find(id);
        if (request.getName() != null) gateway.setName(request.getName());
        if (request.getPhoneNumber() != null) gateway.setPhoneNumber(request.getPhoneNumber());
        if (request.getStatus() != null) gateway.setStatus(request.getStatus());
        applyUssdPin(gateway, request.getUssdPin(), false);
        gateway = gatewayRepository.save(gateway);
        wsPublisher.publish(toResponse(gateway));
        return toResponse(gateway);
    }

    @Transactional
    public void deactivate(Long id) {
        GatewayDevice gateway = find(id);
        gateway.setStatus(GatewayStatus.DISABLED);
        gateway = gatewayRepository.save(gateway);
        log(id, "DEACTIVATE", "Gateway disabled");
        wsPublisher.publish(toResponse(gateway));
    }

    /**
     * Hard-delete gateway. Status/log history cascades; optional FKs are nulled to preserve SMS/TX history.
     */
    @Transactional
    public void delete(Long id) {
        GatewayDevice gateway = find(id);
        jdbcTemplate.update("UPDATE transactions SET gateway_id = NULL WHERE gateway_id = ?", id);
        jdbcTemplate.update("UPDATE sms SET gateway_id = NULL WHERE gateway_id = ?", id);
        jdbcTemplate.update("UPDATE notifications SET gateway_id = NULL WHERE gateway_id = ?", id);
        jdbcTemplate.update("UPDATE distributor_uv_purchases SET gateway_id = NULL WHERE gateway_id = ?", id);
        // gateway_status / gateway_logs: ON DELETE CASCADE
        gatewayRepository.delete(gateway);
    }

    @Transactional
    public GatewayResponse heartbeat(Long id, HeartbeatRequest request) {
        GatewayDevice gateway = find(id);
        gateway.setBatteryLevel(request.getBattery());
        gateway.setNetworkStrength(request.getNetwork());
        gateway.setLatitude(request.getLatitude());
        gateway.setLongitude(request.getLongitude());
        gateway.setMemoryFreeMb(request.getMemory());
        gateway.setStorageFreeMb(request.getStorage());
        gateway.setTemperature(request.getTemp());
        gateway.setInternetAvailable(request.getInternet() == null || request.getInternet());
        gateway.setLastHeartbeatAt(Instant.now());
        gateway.setStatus(GatewayStatus.ONLINE);
        if (gateway.getLoadScore() == 0) {
            gateway.setLastIdleAt(Instant.now());
        }
        gateway = gatewayRepository.save(gateway);

        statusRepository.save(GatewayStatusSnapshot.builder()
                .gatewayId(id)
                .batteryLevel(request.getBattery())
                .networkStrength(request.getNetwork())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .memoryFreeMb(request.getMemory())
                .storageFreeMb(request.getStorage())
                .temperature(request.getTemp())
                .internetAvailable(gateway.isInternetAvailable())
                .recordedAt(Instant.now())
                .build());

        log(id, "HEARTBEAT", "battery=" + request.getBattery() + ", network=" + request.getNetwork());
        GatewayResponse response = toResponse(gateway);
        response.setPendingTasks(taskQueueService.pendingCount(id));
        response.setNextPollSeconds(30);
        wsPublisher.publish(response);
        return response;
    }

    @Transactional(readOnly = true)
    public GatewayResponse select(String operator) {
        return toResponse(selectionService.selectBest(operator));
    }

    private GatewayDevice find(Long id) {
        return gatewayRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATEWAY_NOT_FOUND));
    }

    private void log(Long gatewayId, String type, String message) {
        logRepository.save(GatewayLog.builder()
                .gatewayId(gatewayId)
                .eventType(type)
                .message(message)
                .createdAt(Instant.now())
                .build());
    }

    private GatewayResponse toResponse(GatewayDevice g) {
        return GatewayResponse.builder()
                .id(g.getId()).deviceId(g.getDeviceId()).name(g.getName()).operator(g.getOperator())
                .phoneNumber(g.getPhoneNumber()).status(g.getStatus()).loadScore(g.getLoadScore())
                .batteryLevel(g.getBatteryLevel()).networkStrength(g.getNetworkStrength())
                .latitude(g.getLatitude()).longitude(g.getLongitude())
                .memoryFreeMb(g.getMemoryFreeMb()).storageFreeMb(g.getStorageFreeMb())
                .temperature(g.getTemperature()).internetAvailable(g.isInternetAvailable())
                .lastHeartbeatAt(g.getLastHeartbeatAt()).lastIdleAt(g.getLastIdleAt())
                .ussdPinSet(g.getUssdPin() != null && !g.getUssdPin().isBlank())
                .build();
    }

    private void applyUssdPin(GatewayDevice gateway, String rawPin, boolean required) {
        if (rawPin == null || rawPin.isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le code PIN USSD (4 à 6 chiffres) est obligatoire");
            }
            return;
        }
        gateway.setUssdPin(normalizeUssdPin(rawPin, true));
    }

    private String normalizeUssdPin(String pin, boolean required) {
        if (pin == null || pin.isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le code PIN USSD (4 à 6 chiffres) est obligatoire");
            }
            return null;
        }
        String cleaned = pin.replaceAll("\\D", "");
        if (!cleaned.matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le code PIN USSD doit contenir 4 à 6 chiffres");
        }
        return cleaned;
    }
}