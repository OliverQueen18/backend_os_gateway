package com.osgateway.user.application;

import com.osgateway.common.dto.PageResponse;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import com.osgateway.user.api.dto.UserDtos.*;
import com.osgateway.user.domain.*;
import com.osgateway.user.infrastructure.persistence.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final DistributorAccountRepository distributorRepository;
    private final DistributorUvPurchaseRepository uvPurchaseRepository;
    private final CommissionPayoutRepository commissionPayoutRepository;
    private final DistributorAttachmentRepository attachmentRepository;
    private final DistributorRegistrationService registrationService;
    private final OperationTypeRepository operationTypeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Comptes seed / anciens sans PIN → PIN temporaire 1234 (à changer). */
    @PostConstruct
    void bootstrapMissingPins() {
        try {
            String hash = passwordEncoder.encode("1234");
            int updated = jdbcTemplate.update(
                    "UPDATE distributor_accounts SET pin_hash = ? WHERE pin_hash IS NULL OR pin_hash = ''",
                    hash);
            if (updated > 0) {
                // no-op log via stdout for ops visibility
                System.out.println("[user-service] PIN par défaut 1234 appliqué à " + updated + " distributeur(s)");
            }
        } catch (Exception ignored) {
            // colonne absente tant que la migration n'est pas appliquée
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(String q, Boolean enabled, int page, int size) {
        // Never pass null String to JPQL LIKE/LOWER — Postgres binds it as bytea and fails.
        String query = q == null || q.isBlank() ? "" : q.trim();
        var pageable = PageRequest.of(page, size);
        Page<UserEntity> result;
        if (query.isEmpty() && enabled == null) {
            result = userRepository.findAll(pageable);
        } else if (query.isEmpty()) {
            result = userRepository.findByEnabled(enabled, pageable);
        } else {
            result = userRepository.search(query, enabled, pageable);
        }
        List<UserResponse> content = result.getContent().stream().map(this::toUserResponse).toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        return toUserResponse(userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)));
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "ChangeMe@123"))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();
        user.setCreatedBy("system");
        user = userRepository.save(user);
        assignRoles(user.getId(), request.getRoles());
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        user = userRepository.save(user);
        if (request.getRoles() != null) {
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", id);
            assignRoles(id, request.getRoles());
        }
        return toUserResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", id);
        userRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(this::toRoleResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(Long id) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        return toRoleResponse(role);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String name = request.getName().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.findByName(name).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Role already exists: " + name);
        }
        RoleEntity role = roleRepository.save(RoleEntity.builder()
                .name(name)
                .description(request.getDescription())
                .build());
        replaceRolePermissions(role.getId(), request.getPermissions());
        return toRoleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim().toUpperCase(Locale.ROOT);
            roleRepository.findByName(name).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Role already exists: " + name);
                }
            });
            role.setName(name);
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        role = roleRepository.save(role);
        if (request.getPermissions() != null) {
            replaceRolePermissions(id, request.getPermissions());
        }
        return toRoleResponse(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        if ("ADMIN".equalsIgnoreCase(role.getName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Cannot delete ADMIN role");
        }
        Integer users = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE role_id = ?", Integer.class, id);
        if (users != null && users > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Role is assigned to users");
        }
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", id);
        roleRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (permissionRepository.findByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Permission already exists: " + code);
        }
        PermissionEntity p = permissionRepository.save(PermissionEntity.builder()
                .code(code)
                .description(request.getDescription() != null ? request.getDescription() : code)
                .build());
        return PermissionResponse.builder().id(p.getId()).code(p.getCode()).description(p.getDescription()).build();
    }

    @Transactional
    public PermissionResponse updatePermission(Long id, PermissionRequest request) {
        PermissionEntity p = permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Permission not found"));
        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = request.getCode().trim().toUpperCase(Locale.ROOT);
            permissionRepository.findByCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Permission already exists: " + code);
                }
            });
            p.setCode(code);
        }
        if (request.getDescription() != null) {
            p.setDescription(request.getDescription());
        }
        p = permissionRepository.save(p);
        return PermissionResponse.builder().id(p.getId()).code(p.getCode()).description(p.getDescription()).build();
    }

    @Transactional
    public void deletePermission(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Permission not found");
        }
        jdbcTemplate.update("DELETE FROM role_permissions WHERE permission_id = ?", id);
        permissionRepository.deleteById(id);
    }

    @Transactional
    public void deactivateDistributor(Long id) {
        DistributorAccount account = distributorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        account.setActive(false);
        distributorRepository.save(account);
        userRepository.findById(account.getUserId()).ifPresent(user -> {
            user.setEnabled(false);
            userRepository.save(user);
        });
    }

    /**
     * Hard-delete distributor account and linked login user.
     * UV purchase rows are removed; historical transactions keep their data with distributor_id nulled.
     */
    @Transactional
    public void deleteDistributor(Long id) {
        DistributorAccount account = distributorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        Long userId = account.getUserId();

        jdbcTemplate.update("DELETE FROM distributor_uv_purchases WHERE distributor_id = ?", id);
        jdbcTemplate.update("UPDATE transactions SET distributor_id = NULL WHERE distributor_id = ?", id);
        distributorRepository.delete(account);

        if (userId != null && userRepository.existsById(userId)) {
            // Avoid FK issues on optional references before removing the login account.
            jdbcTemplate.update("UPDATE notifications SET user_id = NULL WHERE user_id = ?", userId);
            jdbcTemplate.update("UPDATE transactions SET user_id = NULL WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
    }

    @Transactional
    public void deleteOperationType(Long id) {
        OperationType type = operationTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Operation type not found"));
        type.setActive(false);
        operationTypeRepository.save(type);
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> listSettings() {
        return jdbcTemplate.query(
                "SELECT id, key, value, description FROM settings ORDER BY key",
                (rs, rowNum) -> SettingResponse.builder()
                        .id(rs.getLong("id"))
                        .key(rs.getString("key"))
                        .value(rs.getString("value"))
                        .description(rs.getString("description"))
                        .build());
    }

    @Transactional
    public List<SettingResponse> upsertSettings(List<SettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return listSettings();
        }
        for (SettingRequest req : requests) {
            jdbcTemplate.update(
                    """
                    INSERT INTO settings(key, value, description, updated_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (key) DO UPDATE
                    SET value = EXCLUDED.value,
                        description = COALESCE(EXCLUDED.description, settings.description),
                        updated_at = NOW()
                    """,
                    req.getKey(),
                    req.getValue(),
                    req.getDescription());
        }
        return listSettings();
    }

    private void replaceRolePermissions(Long roleId, List<String> permissions) {
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        if (permissions == null) return;
        for (String code : permissions) {
            if (code == null || code.isBlank()) continue;
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            PermissionEntity p = permissionRepository.findByCode(normalized)
                    .orElseGet(() -> permissionRepository.save(
                            PermissionEntity.builder().code(normalized).description(normalized).build()));
            jdbcTemplate.update(
                    "INSERT INTO role_permissions(role_id, permission_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    roleId, p.getId());
        }
    }

    private RoleResponse toRoleResponse(RoleEntity role) {
        List<String> permissions = jdbcTemplate.queryForList(
                """
                SELECT p.code FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                ORDER BY p.code
                """,
                String.class, role.getId());
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissions)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<DistributorResponse> listDistributors(Boolean active, String registrationStatus, int page, int size) {
        Page<DistributorAccount> result;
        boolean hasStatus = registrationStatus != null && !registrationStatus.isBlank();
        if (active == null && !hasStatus) {
            result = distributorRepository.findAll(PageRequest.of(page, size));
        } else if (active != null && hasStatus) {
            result = distributorRepository.findByActiveAndRegistrationStatus(
                    active, registrationStatus.trim().toUpperCase(Locale.ROOT), PageRequest.of(page, size));
        } else if (hasStatus) {
            result = distributorRepository.findByRegistrationStatus(
                    registrationStatus.trim().toUpperCase(Locale.ROOT), PageRequest.of(page, size));
        } else {
            result = distributorRepository.findByActive(active, PageRequest.of(page, size));
        }
        List<DistributorAccount> accounts = result.getContent();
        List<DistributorResponse> content = toDistributorList(accounts);
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DistributorResponse getDistributor(Long id) {
        DistributorAccount account = distributorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        return toDistributor(account);
    }

    @Transactional(readOnly = true)
    public DistributorResponse getMyDistributor(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        DistributorAccount account = distributorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor account not found"));
        return toDistributor(account);
    }

    @Transactional
    public void changeMyPin(Long userId, ChangePinRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        DistributorAccount account = distributorRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor account not found"));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Distributor is inactive");
        }

        String current = normalizePin(request.getCurrentPin(), true);
        String next = normalizePin(request.getNewPin(), true);
        if (current.equals(next)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le nouveau PIN doit être différent de l'actuel");
        }

        String hash = account.getPinHash();
        if (hash == null || hash.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Aucun PIN configuré — contactez l'administrateur");
        }
        if (!passwordEncoder.matches(current, hash)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Code PIN actuel incorrect");
        }

        account.setPinHash(passwordEncoder.encode(next));
        distributorRepository.save(account);
    }

    @Transactional
    public void changeMyPassword(Long userId, ChangePasswordRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        String current = request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
        String next = request.getNewPassword() == null ? "" : request.getNewPassword().trim();
        if (next.length() < 6) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le nouveau mot de passe doit contenir au moins 6 caractères");
        }
        if (current.equals(next)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le nouveau mot de passe doit être différent de l'actuel");
        }
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Mot de passe actuel incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        userRepository.save(user);
    }

    @Transactional
    public DistributorResponse createDistributor(DistributorRequest request) {
        String pin = normalizePin(request.getPin(), true);
        String password = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword().trim()
                : "ChangeMe@123";

        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (distributorRepository.findByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Ce code distributeur existe déjà : " + code);
        }

        CreatedDistributorUser createdUser = null;
        Long userId = request.getUserId();
        if (userId == null) {
            createdUser = createDistributorUser(request, password);
            userId = createdUser.userId();
        }
        String agencyName = request.getName() != null && !request.getName().isBlank()
                ? request.getName()
                : (request.getFirstName() + " " + request.getLastName()).trim();
        DistributorAccount account = DistributorAccount.builder()
                .userId(userId)
                .code(code)
                .name(agencyName)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .rccm(request.getRccm())
                .nif(request.getNif())
                .nina(request.getNina())
                .balance(request.getBalance() != null ? request.getBalance() : BigDecimal.ZERO)
                .commissionRate(request.getCommissionRate() != null ? request.getCommissionRate() : new BigDecimal("1.50"))
                .pinHash(passwordEncoder.encode(pin))
                .active(request.getActive() == null || request.getActive())
                .registrationStatus("APPROVED")
                .registrationFeePaid(true)
                .registrationFeeAmount(registrationService.configuredRegistrationFee())
                .registrationFeePaidAt(java.time.Instant.now())
                .submittedAt(java.time.Instant.now())
                .reviewedAt(java.time.Instant.now())
                .build();
        account.setCreatedBy("system");
        DistributorResponse response = toDistributor(distributorRepository.save(account));
        if (createdUser != null) {
            response.setUsername(createdUser.username());
            response.setTemporaryPassword(password);
            registrationService.notifyUser(
                    userId,
                    "REGISTRATION_APPROVED",
                    "Compte distributeur créé",
                    "Votre compte a été créé et validé par l'administrateur.",
                    "INFO");
        }
        return response;
    }

    @Transactional
    public DistributorResponse updateDistributor(Long id, DistributorRequest request) {
        DistributorAccount account = distributorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        if (request.getName() != null) account.setName(request.getName());
        if (request.getFirstName() != null) account.setFirstName(request.getFirstName());
        if (request.getLastName() != null) account.setLastName(request.getLastName());
        if (request.getEmail() != null) account.setEmail(request.getEmail());
        if (request.getPhone() != null) account.setPhone(request.getPhone());
        if (request.getAddress() != null) account.setAddress(request.getAddress());
        if (request.getLatitude() != null) account.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) account.setLongitude(request.getLongitude());
        if (request.getRccm() != null) account.setRccm(request.getRccm());
        if (request.getNif() != null) account.setNif(request.getNif());
        if (request.getNina() != null) account.setNina(request.getNina());
        if (request.getCommissionRate() != null) account.setCommissionRate(request.getCommissionRate());
        if (request.getActive() != null) account.setActive(request.getActive());
        // balance is managed via UV purchases / TX debit — allow admin adjustment only if explicitly sent
        if (request.getBalance() != null) account.setBalance(request.getBalance());
        if (request.getPin() != null && !request.getPin().isBlank()) {
            account.setPinHash(passwordEncoder.encode(normalizePin(request.getPin(), true)));
        }

        if (account.getName() == null || account.getName().isBlank()) {
            account.setName((account.getFirstName() + " " + account.getLastName()).trim());
        }
        syncDistributorUser(account);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            userRepository.findById(account.getUserId()).ifPresent(user -> {
                user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
                userRepository.save(user);
            });
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String username = resolveUsername(request.getUsername(), account.getCode());
            userRepository.findById(account.getUserId()).ifPresent(user -> {
                if (!username.equalsIgnoreCase(user.getUsername())
                        && userRepository.existsByUsername(username)) {
                    throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS,
                            "Ce login est déjà utilisé : " + username);
                }
                user.setUsername(username);
                userRepository.save(user);
            });
        }
        return toDistributor(distributorRepository.save(account));
    }

    @Transactional
    public UvPurchaseResponse purchaseUv(Long distributorId, UvPurchaseRequest request) {
        DistributorAccount account = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Distributor is inactive");
        }

        String method = request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!method.equals("CASH") && !method.equals("GATEWAY_DEPOSIT")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "paymentMethod must be CASH or GATEWAY_DEPOSIT");
        }
        if (method.equals("GATEWAY_DEPOSIT") && request.getGatewayId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "gatewayId is required for GATEWAY_DEPOSIT");
        }

        BigDecimal newBalance = account.getBalance().add(request.getAmount());
        account.setBalance(newBalance);
        distributorRepository.save(account);

        DistributorUvPurchase purchase = DistributorUvPurchase.builder()
                .distributorId(distributorId)
                .amount(request.getAmount())
                .paymentMethod(method)
                .gatewayId(request.getGatewayId())
                .reference("UV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .note(request.getNote())
                .balanceAfter(newBalance)
                .createdBy("admin")
                .build();
        purchase = uvPurchaseRepository.save(purchase);
        return toUvPurchase(purchase);
    }

    @Transactional(readOnly = true)
    public List<UvPurchaseResponse> listUvPurchases(Long distributorId) {
        if (!distributorRepository.existsById(distributorId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found");
        }
        return uvPurchaseRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId).stream()
                .map(this::toUvPurchase)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommissionBalanceResponse getCommissionBalance(Long distributorId) {
        DistributorAccount account = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        return buildCommissionBalance(account);
    }

    @Transactional(readOnly = true)
    public List<CommissionBalanceResponse> listCommissionBalances() {
        List<DistributorAccount> accounts = distributorRepository.findAll();
        Map<Long, BigDecimal> earnedById = new HashMap<>();
        Map<Long, BigDecimal> paidById = new HashMap<>();
        try {
            jdbcTemplate.query(
                    """
                    SELECT distributor_id, COALESCE(SUM(distributor_commission), 0) AS earned
                    FROM transactions
                    WHERE status = 'SUCCESS' AND distributor_id IS NOT NULL
                    GROUP BY distributor_id
                    """,
                    (rs) -> {
                        earnedById.put(rs.getLong("distributor_id"), rs.getBigDecimal("earned"));
                    });
        } catch (Exception ignored) {
            // table absente / migration non appliquée
        }
        try {
            jdbcTemplate.query(
                    """
                    SELECT distributor_id, COALESCE(SUM(amount), 0) AS paid
                    FROM commission_payouts
                    GROUP BY distributor_id
                    """,
                    (rs) -> {
                        paidById.put(rs.getLong("distributor_id"), rs.getBigDecimal("paid"));
                    });
        } catch (Exception ignored) {
            // table absente / migration non appliquée
        }
        return accounts.stream()
                .map(account -> {
                    BigDecimal earned = earnedById.getOrDefault(account.getId(), BigDecimal.ZERO);
                    BigDecimal paid = paidById.getOrDefault(account.getId(), BigDecimal.ZERO);
                    BigDecimal unpaid = earned.subtract(paid);
                    if (unpaid.compareTo(BigDecimal.ZERO) < 0) unpaid = BigDecimal.ZERO;
                    String name = account.getName();
                    if (name == null || name.isBlank()) {
                        name = ((account.getFirstName() != null ? account.getFirstName() : "") + " "
                                + (account.getLastName() != null ? account.getLastName() : "")).trim();
                    }
                    return CommissionBalanceResponse.builder()
                            .distributorId(account.getId())
                            .distributorCode(account.getCode())
                            .distributorName(name)
                            .earned(earned.setScale(2, java.math.RoundingMode.HALF_UP))
                            .paid(paid.setScale(2, java.math.RoundingMode.HALF_UP))
                            .unpaid(unpaid.setScale(2, java.math.RoundingMode.HALF_UP))
                            .build();
                })
                .toList();
    }

    @Transactional
    public CommissionPayoutResponse payCommission(Long distributorId, CommissionPayoutRequest request) {
        DistributorAccount account = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found"));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Distributor is inactive");
        }

        String method = request.getPaymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CASH", "BANK_TRANSFER", "MOBILE_MONEY", "OTHER").contains(method)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "paymentMethod must be CASH, BANK_TRANSFER, MOBILE_MONEY or OTHER");
        }

        BigDecimal amount = request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "amount must be > 0");
        }

        CommissionBalanceResponse balance = buildCommissionBalance(account);
        if (amount.compareTo(balance.getUnpaid()) > 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "Montant supérieur au solde commission dû (" + balance.getUnpaid() + " XOF)");
        }

        BigDecimal unpaidAfter = balance.getUnpaid().subtract(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        String reference = request.getReference() != null && !request.getReference().isBlank()
                ? request.getReference().trim()
                : "CP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        CommissionPayout payout = CommissionPayout.builder()
                .distributorId(distributorId)
                .amount(amount)
                .paymentMethod(method)
                .reference(reference)
                .note(request.getNote())
                .unpaidAfter(unpaidAfter)
                .createdBy("admin")
                .build();
        payout = commissionPayoutRepository.save(payout);
        return toCommissionPayout(payout);
    }

    @Transactional(readOnly = true)
    public List<CommissionPayoutResponse> listCommissionPayouts(Long distributorId) {
        if (!distributorRepository.existsById(distributorId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Distributor not found");
        }
        return commissionPayoutRepository.findByDistributorIdOrderByPaidAtDesc(distributorId).stream()
                .map(this::toCommissionPayout)
                .toList();
    }

    private CommissionBalanceResponse buildCommissionBalance(DistributorAccount account) {
        BigDecimal earned = sumEarnedCommission(account.getId());
        BigDecimal paid = commissionPayoutRepository.sumAmountByDistributorId(account.getId());
        if (paid == null) paid = BigDecimal.ZERO;
        BigDecimal unpaid = earned.subtract(paid);
        if (unpaid.compareTo(BigDecimal.ZERO) < 0) unpaid = BigDecimal.ZERO;
        String name = account.getName();
        if (name == null || name.isBlank()) {
            name = ((account.getFirstName() != null ? account.getFirstName() : "") + " "
                    + (account.getLastName() != null ? account.getLastName() : "")).trim();
        }
        return CommissionBalanceResponse.builder()
                .distributorId(account.getId())
                .distributorCode(account.getCode())
                .distributorName(name)
                .earned(earned.setScale(2, java.math.RoundingMode.HALF_UP))
                .paid(paid.setScale(2, java.math.RoundingMode.HALF_UP))
                .unpaid(unpaid.setScale(2, java.math.RoundingMode.HALF_UP))
                .build();
    }

    private BigDecimal sumEarnedCommission(Long distributorId) {
        try {
            BigDecimal earned = jdbcTemplate.queryForObject(
                    """
                    SELECT COALESCE(SUM(distributor_commission), 0)
                    FROM transactions
                    WHERE distributor_id = ? AND status = 'SUCCESS'
                    """,
                    BigDecimal.class,
                    distributorId);
            return earned != null ? earned : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    @Transactional(readOnly = true)
    public List<OperationTypeResponse> listOperationTypes(Boolean activeOnly) {
        List<OperationType> list = Boolean.TRUE.equals(activeOnly)
                ? operationTypeRepository.findByActiveTrueOrderByLabelAsc()
                : operationTypeRepository.findAllByOrderByLabelAsc();
        return list.stream().map(this::toOperationType).toList();
    }

    @Transactional
    public OperationTypeResponse createOperationType(OperationTypeRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (operationTypeRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Operation type already exists: " + code);
        }
        CommissionConfig commission = resolveCommissionConfig(
                request.getCommissionMode(),
                request.getCommissionValue(),
                request.getAdminSharePercent(),
                request.getDistributorSharePercent());
        OperationType type = OperationType.builder()
                .code(code)
                .label(request.getLabel())
                .description(request.getDescription())
                .icon(normalizeIcon(request.getIcon()))
                .balanceEffect(normalizeBalanceEffect(request.getBalanceEffect()))
                .commissionMode(commission.mode())
                .commissionValue(commission.value())
                .adminSharePercent(commission.adminShare())
                .distributorSharePercent(commission.distributorShare())
                .active(request.getActive() == null || request.getActive())
                .cancellable(request.getCancellable() == null || request.getCancellable())
                .requiresPhone(request.getRequiresPhone() == null || request.getRequiresPhone())
                .requiresAmount(request.getRequiresAmount() == null || request.getRequiresAmount())
                .build();
        type.setCreatedBy("admin");
        return toOperationType(operationTypeRepository.save(type));
    }

    @Transactional
    public OperationTypeResponse updateOperationType(Long id, OperationTypeRequest request) {
        OperationType type = operationTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Operation type not found"));
        if (request.getLabel() != null) type.setLabel(request.getLabel());
        if (request.getDescription() != null) type.setDescription(request.getDescription());
        if (request.getIcon() != null) type.setIcon(normalizeIcon(request.getIcon()));
        if (request.getBalanceEffect() != null) type.setBalanceEffect(normalizeBalanceEffect(request.getBalanceEffect()));
        if (request.getActive() != null) type.setActive(request.getActive());
        if (request.getCancellable() != null) type.setCancellable(request.getCancellable());
        if (request.getRequiresPhone() != null) type.setRequiresPhone(request.getRequiresPhone());
        if (request.getRequiresAmount() != null) type.setRequiresAmount(request.getRequiresAmount());
        if (request.getCommissionMode() != null
                || request.getCommissionValue() != null
                || request.getAdminSharePercent() != null
                || request.getDistributorSharePercent() != null) {
            CommissionConfig commission = resolveCommissionConfig(
                    request.getCommissionMode() != null ? request.getCommissionMode() : type.getCommissionMode(),
                    request.getCommissionValue() != null ? request.getCommissionValue() : type.getCommissionValue(),
                    request.getAdminSharePercent() != null ? request.getAdminSharePercent() : type.getAdminSharePercent(),
                    request.getDistributorSharePercent() != null
                            ? request.getDistributorSharePercent()
                            : type.getDistributorSharePercent());
            type.setCommissionMode(commission.mode());
            type.setCommissionValue(commission.value());
            type.setAdminSharePercent(commission.adminShare());
            type.setDistributorSharePercent(commission.distributorShare());
        }
        // code changes carefully
        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = request.getCode().trim().toUpperCase(Locale.ROOT);
            if (!code.equalsIgnoreCase(type.getCode()) && operationTypeRepository.existsByCodeIgnoreCase(code)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Operation type already exists: " + code);
            }
            type.setCode(code);
        }
        return toOperationType(operationTypeRepository.save(type));
    }

    private CreatedDistributorUser createDistributorUser(DistributorRequest request, String password) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS,
                    "Un utilisateur existe déjà avec cet email");
        }
        String username = resolveUsername(request.getUsername(), request.getCode());
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS,
                    "Ce login est déjà utilisé : " + username);
        }
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(password))
                .fullName((request.getFirstName() + " " + request.getLastName()).trim())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        user.setCreatedBy("system");
        user = userRepository.save(user);
        assignRoles(user.getId(), List.of("DISTRIBUTEUR"));
        return new CreatedDistributorUser(user.getId(), username);
    }

    private String resolveUsername(String requested, String code) {
        String raw = requested != null && !requested.isBlank() ? requested : code;
        String username = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (username.isBlank()) {
            username = "dist" + System.currentTimeMillis();
        }
        return username;
    }

    private String normalizePin(String pin, boolean required) {
        if (pin == null || pin.isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Le code PIN (4 à 6 chiffres) est obligatoire");
            }
            return null;
        }
        String cleaned = pin.trim();
        if (!cleaned.matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le code PIN doit contenir 4 à 6 chiffres");
        }
        return cleaned;
    }

    private record CreatedDistributorUser(Long userId, String username) {}

    private void syncDistributorUser(DistributorAccount account) {
        userRepository.findById(account.getUserId()).ifPresent(user -> {
            if (account.getEmail() != null) user.setEmail(account.getEmail());
            if (account.getPhone() != null) user.setPhone(account.getPhone());
            String full = ((account.getFirstName() != null ? account.getFirstName() : "") + " "
                    + (account.getLastName() != null ? account.getLastName() : "")).trim();
            if (!full.isBlank()) user.setFullName(full);
            userRepository.save(user);
        });
    }

    private String normalizeBalanceEffect(String effect) {
        if (effect == null || effect.isBlank()) return "DEBIT";
        String v = effect.trim().toUpperCase(Locale.ROOT);
        if (!v.equals("DEBIT") && !v.equals("CREDIT") && !v.equals("NONE")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "balanceEffect must be DEBIT, CREDIT or NONE");
        }
        return v;
    }

    private CommissionConfig resolveCommissionConfig(
            String mode,
            BigDecimal value,
            BigDecimal adminShare,
            BigDecimal distributorShare) {
        String normalizedMode = mode == null || mode.isBlank() ? "PERCENT" : mode.trim().toUpperCase(Locale.ROOT);
        if (!"PERCENT".equals(normalizedMode) && !"FIXED".equals(normalizedMode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "commissionMode must be PERCENT or FIXED");
        }
        BigDecimal normalizedValue = value != null ? value : new BigDecimal("1.50");
        if (normalizedValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "commissionValue must be >= 0");
        }
        BigDecimal admin = adminShare != null ? adminShare : new BigDecimal("40.00");
        BigDecimal distributor = distributorShare != null ? distributorShare : new BigDecimal("60.00");
        if (admin.compareTo(BigDecimal.ZERO) < 0 || distributor.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "commission shares must be >= 0");
        }
        if (admin.add(distributor).compareTo(new BigDecimal("100")) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "admin + distributor shares must equal 100");
        }
        return new CommissionConfig(normalizedMode, normalizedValue, admin, distributor);
    }

    private record CommissionConfig(
            String mode,
            BigDecimal value,
            BigDecimal adminShare,
            BigDecimal distributorShare) {}

    private String normalizeIcon(String icon) {
        if (icon == null || icon.isBlank()) return "pi pi-bolt";
        String v = icon.trim();
        if (!v.startsWith("pi ")) {
            v = v.startsWith("pi-") ? "pi " + v : "pi pi-" + v.replaceFirst("^pi-", "");
        }
        if (v.length() > 80) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "icon too long");
        }
        return v;
    }

    private void assignRoles(Long userId, List<String> roles) {
        if (roles == null) return;
        for (String roleName : roles) {
            RoleEntity role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, roleName));
            jdbcTemplate.update("INSERT INTO user_roles(user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    userId, role.getId());
        }
    }

    private UserResponse toUserResponse(UserEntity user) {
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, user.getId());
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .enabled(user.isEnabled())
                .roles(roles)
                .build();
    }

    private List<DistributorResponse> toDistributorList(List<DistributorAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = accounts.stream()
                .map(DistributorAccount::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> usernames = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername, (a, b) -> a));

        List<Long> distributorIds = accounts.stream().map(DistributorAccount::getId).toList();
        Map<Long, Integer> attachmentCounts = new HashMap<>();
        if (!distributorIds.isEmpty()) {
            for (Object[] row : attachmentRepository.countGroupedByDistributorIds(distributorIds)) {
                Long distributorId = (Long) row[0];
                Number count = (Number) row[1];
                attachmentCounts.put(distributorId, count.intValue());
            }
        }

        return accounts.stream()
                .map(a -> toDistributor(
                        a,
                        usernames.get(a.getUserId()),
                        attachmentCounts.getOrDefault(a.getId(), 0)))
                .toList();
    }

    private DistributorResponse toDistributor(DistributorAccount a) {
        String username = userRepository.findById(a.getUserId())
                .map(UserEntity::getUsername)
                .orElse(null);
        int attachmentCount = (int) attachmentRepository.countByDistributorId(a.getId());
        return toDistributor(a, username, attachmentCount);
    }

    private DistributorResponse toDistributor(DistributorAccount a, String username, int attachmentCount) {
        return DistributorResponse.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .code(a.getCode())
                .name(a.getName())
                .firstName(a.getFirstName())
                .lastName(a.getLastName())
                .email(a.getEmail())
                .phone(a.getPhone())
                .address(a.getAddress())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .rccm(a.getRccm())
                .nif(a.getNif())
                .nina(a.getNina())
                .balance(a.getBalance())
                .commissionRate(a.getCommissionRate())
                .active(a.isActive())
                .createdAt(a.getCreatedAt())
                .username(username)
                .hasPin(a.getPinHash() != null && !a.getPinHash().isBlank())
                .registrationStatus(a.getRegistrationStatus() != null ? a.getRegistrationStatus() : "APPROVED")
                .registrationFeeAmount(a.getRegistrationFeeAmount())
                .registrationFeePaid(a.isRegistrationFeePaid())
                .registrationFeePaidAt(a.getRegistrationFeePaidAt())
                .registrationFeePaymentRef(a.getRegistrationFeePaymentRef())
                .registrationFeePaymentMethod(a.getRegistrationFeePaymentMethod())
                .rejectionReason(a.getRejectionReason())
                .reviewedAt(a.getReviewedAt())
                .submittedAt(a.getSubmittedAt())
                .attachmentCount(attachmentCount)
                .build();
    }

    private UvPurchaseResponse toUvPurchase(DistributorUvPurchase p) {
        return UvPurchaseResponse.builder()
                .id(p.getId())
                .distributorId(p.getDistributorId())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .gatewayId(p.getGatewayId())
                .reference(p.getReference())
                .note(p.getNote())
                .balanceAfter(p.getBalanceAfter())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private CommissionPayoutResponse toCommissionPayout(CommissionPayout p) {
        return CommissionPayoutResponse.builder()
                .id(p.getId())
                .distributorId(p.getDistributorId())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .reference(p.getReference())
                .note(p.getNote())
                .unpaidAfter(p.getUnpaidAfter())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .createdBy(p.getCreatedBy())
                .build();
    }

    private OperationTypeResponse toOperationType(OperationType t) {
        return OperationTypeResponse.builder()
                .id(t.getId())
                .code(t.getCode())
                .label(t.getLabel())
                .description(t.getDescription())
                .icon(t.getIcon() == null || t.getIcon().isBlank() ? "pi pi-bolt" : t.getIcon())
                .balanceEffect(t.getBalanceEffect())
                .commissionMode(t.getCommissionMode() != null ? t.getCommissionMode() : "PERCENT")
                .commissionValue(t.getCommissionValue() != null ? t.getCommissionValue() : new BigDecimal("1.50"))
                .adminSharePercent(t.getAdminSharePercent() != null ? t.getAdminSharePercent() : new BigDecimal("40.00"))
                .distributorSharePercent(
                        t.getDistributorSharePercent() != null ? t.getDistributorSharePercent() : new BigDecimal("60.00"))
                .active(t.isActive())
                .cancellable(t.isCancellable())
                .requiresPhone(t.isRequiresPhone())
                .requiresAmount(t.isRequiresAmount())
                .build();
    }
}
