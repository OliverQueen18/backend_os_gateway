package com.osgateway.user.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.user.api.dto.UserDtos.UserRequest;
import com.osgateway.user.domain.UserEntity;
import com.osgateway.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock DistributorAccountRepository distributorRepository;
    @Mock DistributorUvPurchaseRepository uvPurchaseRepository;
    @Mock CommissionPayoutRepository commissionPayoutRepository;
    @Mock DistributorAttachmentRepository attachmentRepository;
    @Mock DistributorRegistrationService registrationService;
    @Mock OperationTypeRepository operationTypeRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks UserService userService;

    @Test
    void createUser_duplicateUsername_throws() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);
        UserRequest req = new UserRequest();
        req.setUsername("admin");
        req.setEmail("a@b.com");
        assertThrows(BusinessException.class, () -> userService.createUser(req));
    }

    @Test
    void getUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> userService.getUser(99L));
    }
}