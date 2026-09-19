package com.osgateway.user.api;

import com.osgateway.common.dto.ApiResponse;
import com.osgateway.common.dto.PageResponse;
import com.osgateway.user.api.dto.UserDtos.*;
import com.osgateway.user.application.DistributorRegistrationService;
import com.osgateway.user.application.UserService;
import com.osgateway.user.domain.DistributorAttachment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final DistributorRegistrationService registrationService;

    @GetMapping("/users")
    @Operation(summary = "Search users with pagination")
    public ApiResponse<PageResponse<UserResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.searchUsers(q, enabled, page, size));
    }

    @GetMapping("/users/me")
    @Operation(summary = "Current authenticated user profile")
    public ApiResponse<UserResponse> me(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(userService.getUser(userId));
    }

    @PostMapping("/users/me/password")
    @Operation(summary = "Change password for the authenticated user")
    public ApiResponse<Void> changeMyPassword(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeMyPassword(userId, request);
        return ApiResponse.ok("Mot de passe mis à jour", null);
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @PostMapping("/users")
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ApiResponse.ok("Created", userService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @RequestBody UserRequest request) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.ok(userService.listRoles());
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<RoleResponse> getRole(@PathVariable Long id) {
        return ApiResponse.ok(userService.getRole(id));
    }

    @PostMapping("/roles")
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok("Created", userService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleResponse> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        return ApiResponse.ok(userService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        userService.deleteRole(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.ok(userService.listPermissions());
    }

    @PostMapping("/permissions")
    public ApiResponse<PermissionResponse> createPermission(@Valid @RequestBody PermissionRequest request) {
        return ApiResponse.ok("Created", userService.createPermission(request));
    }

    @PutMapping("/permissions/{id}")
    public ApiResponse<PermissionResponse> updatePermission(
            @PathVariable Long id, @RequestBody PermissionRequest request) {
        return ApiResponse.ok(userService.updatePermission(id, request));
    }

    @DeleteMapping("/permissions/{id}")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        userService.deletePermission(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/settings")
    public ApiResponse<List<SettingResponse>> settings() {
        return ApiResponse.ok(userService.listSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<List<SettingResponse>> upsertSettings(@RequestBody SettingsBulkRequest request) {
        return ApiResponse.ok(userService.upsertSettings(request.getSettings()));
    }

    @GetMapping("/distributors/me")
    @Operation(summary = "Current authenticated distributor account (UV balance)")
    public ApiResponse<DistributorResponse> myDistributor(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(userService.getMyDistributor(userId));
    }

    @PostMapping("/distributors/me/pin")
    @Operation(summary = "Change transaction PIN for the authenticated distributor")
    public ApiResponse<Void> changeMyPin(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChangePinRequest request) {
        userService.changeMyPin(userId, request);
        return ApiResponse.ok("PIN mis à jour", null);
    }

    @PutMapping("/distributors/me/registration")
    @Operation(summary = "Update KYC registration fields for current distributor")
    public ApiResponse<DistributorResponse> updateMyRegistration(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody RegistrationKycRequest request) {
        registrationService.updateMyKyc(userId, request);
        return ApiResponse.ok(userService.getMyDistributor(userId));
    }

    @PostMapping(value = "/distributors/me/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload registration attachment for current distributor")
    public ApiResponse<AttachmentResponse> uploadMyAttachment(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam String docType,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("Uploaded", registrationService.uploadAttachment(userId, null, docType, file, false));
    }

    @GetMapping("/distributors/me/attachments")
    public ApiResponse<List<AttachmentResponse>> listMyAttachments(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(registrationService.listMyAttachments(userId));
    }

    @PostMapping("/distributors/me/registration-fee")
    @Operation(summary = "Pay distributor registration fee")
    public ApiResponse<DistributorResponse> payMyRegistrationFee(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody RegistrationFeePaymentRequest request) {
        registrationService.payRegistrationFee(userId, request);
        return ApiResponse.ok("Frais enregistrés", userService.getMyDistributor(userId));
    }

    @GetMapping("/distributors/registration-fee")
    @Operation(summary = "Configured registration fee amount")
    public ApiResponse<BigDecimal> registrationFeeAmount() {
        return ApiResponse.ok(registrationService.configuredRegistrationFee());
    }

    @PostMapping("/distributors/{id}/approve")
    @Operation(summary = "Approve distributor registration")
    public ApiResponse<DistributorResponse> approveDistributor(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long reviewerId) {
        registrationService.approve(id, reviewerId);
        return ApiResponse.ok("Approuvé", userService.getDistributor(id));
    }

    @PostMapping("/distributors/{id}/reject")
    @Operation(summary = "Reject distributor registration")
    public ApiResponse<DistributorResponse> rejectDistributor(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long reviewerId,
            @Valid @RequestBody RejectDistributorRequest request) {
        registrationService.reject(id, reviewerId, request);
        return ApiResponse.ok("Rejeté", userService.getDistributor(id));
    }

    @GetMapping("/distributors/{id}/attachments")
    public ApiResponse<List<AttachmentResponse>> listAttachments(@PathVariable Long id) {
        return ApiResponse.ok(registrationService.listAttachments(id));
    }

    @PostMapping(value = "/distributors/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentResponse> uploadAttachmentAdmin(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam String docType,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("Uploaded", registrationService.uploadAttachment(userId, id, docType, file, true));
    }

    @GetMapping("/distributors/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        DistributorAttachment meta = registrationService.getAttachmentMeta(id, attachmentId);
        Resource resource = registrationService.loadAttachment(id, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        meta.getContentType() != null ? meta.getContentType() : "application/octet-stream"))
                .body(resource);
    }

    @GetMapping("/distributors")
    public ApiResponse<PageResponse<DistributorResponse>> distributors(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String registrationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.listDistributors(active, registrationStatus, page, size));
    }

    @GetMapping("/distributors/{id}")
    public ApiResponse<DistributorResponse> getDistributor(@PathVariable Long id) {
        return ApiResponse.ok(userService.getDistributor(id));
    }

    @PostMapping("/distributors")
    public ApiResponse<DistributorResponse> createDistributor(@Valid @RequestBody DistributorRequest request) {
        return ApiResponse.ok(userService.createDistributor(request));
    }

    @PutMapping("/distributors/{id}")
    public ApiResponse<DistributorResponse> updateDistributor(@PathVariable Long id, @RequestBody DistributorRequest request) {
        return ApiResponse.ok(userService.updateDistributor(id, request));
    }

    @PostMapping("/distributors/{id}/deactivate")
    @Operation(summary = "Deactivate distributor account (soft)")
    public ApiResponse<Void> deactivateDistributor(@PathVariable Long id) {
        userService.deactivateDistributor(id);
        return ApiResponse.ok("Deactivated", null);
    }

    @DeleteMapping("/distributors/{id}")
    @Operation(summary = "Permanently delete distributor account and linked user login")
    public ApiResponse<Void> deleteDistributor(@PathVariable Long id) {
        userService.deleteDistributor(id);
        return ApiResponse.ok("Deleted", null);
    }

    @PostMapping("/distributors/{id}/uv-purchases")
    @Operation(summary = "Buy UV for distributor (CASH or GATEWAY_DEPOSIT) — credits UV balance")
    public ApiResponse<UvPurchaseResponse> purchaseUv(
            @PathVariable Long id,
            @Valid @RequestBody UvPurchaseRequest request) {
        return ApiResponse.ok("UV credited", userService.purchaseUv(id, request));
    }

    @GetMapping("/distributors/{id}/uv-purchases")
    public ApiResponse<List<UvPurchaseResponse>> listUvPurchases(@PathVariable Long id) {
        return ApiResponse.ok(userService.listUvPurchases(id));
    }

    @GetMapping("/distributors/commission-balances")
    @Operation(summary = "Commission balances for all distributors (earned / paid / unpaid)")
    public ApiResponse<List<CommissionBalanceResponse>> listCommissionBalances() {
        return ApiResponse.ok(userService.listCommissionBalances());
    }

    @GetMapping("/distributors/{id}/commission-balance")
    @Operation(summary = "Commission balance for one distributor")
    public ApiResponse<CommissionBalanceResponse> getCommissionBalance(@PathVariable Long id) {
        return ApiResponse.ok(userService.getCommissionBalance(id));
    }

    @PostMapping("/distributors/{id}/commission-payouts")
    @Operation(summary = "Pay all or part of distributor earned commission")
    public ApiResponse<CommissionPayoutResponse> payCommission(
            @PathVariable Long id,
            @Valid @RequestBody CommissionPayoutRequest request) {
        return ApiResponse.ok("Commission paid", userService.payCommission(id, request));
    }

    @GetMapping("/distributors/{id}/commission-payouts")
    @Operation(summary = "Commission payout history")
    public ApiResponse<List<CommissionPayoutResponse>> listCommissionPayouts(@PathVariable Long id) {
        return ApiResponse.ok(userService.listCommissionPayouts(id));
    }

    @GetMapping("/operation-types")
    @Operation(summary = "List operation types (admin-managed)")
    public ApiResponse<List<OperationTypeResponse>> listOperationTypes(
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(userService.listOperationTypes(active));
    }

    @PostMapping("/operation-types")
    public ApiResponse<OperationTypeResponse> createOperationType(@Valid @RequestBody OperationTypeRequest request) {
        return ApiResponse.ok("Created", userService.createOperationType(request));
    }

    @PutMapping("/operation-types/{id}")
    public ApiResponse<OperationTypeResponse> updateOperationType(
            @PathVariable Long id,
            @RequestBody OperationTypeRequest request) {
        return ApiResponse.ok(userService.updateOperationType(id, request));
    }

    @DeleteMapping("/operation-types/{id}")
    @Operation(summary = "Soft-delete operation type (deactivate)")
    public ApiResponse<Void> deleteOperationType(@PathVariable Long id) {
        userService.deleteOperationType(id);
        return ApiResponse.ok("Deactivated", null);
    }
}
