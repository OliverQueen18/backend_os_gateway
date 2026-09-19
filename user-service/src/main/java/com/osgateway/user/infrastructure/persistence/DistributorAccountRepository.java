package com.osgateway.user.infrastructure.persistence;

import com.osgateway.user.domain.DistributorAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DistributorAccountRepository extends JpaRepository<DistributorAccount, Long> {
    Optional<DistributorAccount> findByCode(String code);
    Optional<DistributorAccount> findByUserId(Long userId);
    Page<DistributorAccount> findByActive(boolean active, Pageable pageable);

    Page<DistributorAccount> findByRegistrationStatus(String registrationStatus, Pageable pageable);

    Page<DistributorAccount> findByActiveAndRegistrationStatus(boolean active, String registrationStatus, Pageable pageable);
}