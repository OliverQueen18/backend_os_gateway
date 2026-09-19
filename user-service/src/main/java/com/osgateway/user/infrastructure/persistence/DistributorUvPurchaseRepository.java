package com.osgateway.user.infrastructure.persistence;

import com.osgateway.user.domain.DistributorUvPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistributorUvPurchaseRepository extends JpaRepository<DistributorUvPurchase, Long> {
    List<DistributorUvPurchase> findByDistributorIdOrderByCreatedAtDesc(Long distributorId);
}
