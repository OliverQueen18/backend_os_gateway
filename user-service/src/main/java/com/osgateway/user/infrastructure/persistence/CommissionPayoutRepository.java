package com.osgateway.user.infrastructure.persistence;

import com.osgateway.user.domain.CommissionPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CommissionPayoutRepository extends JpaRepository<CommissionPayout, Long> {
    List<CommissionPayout> findByDistributorIdOrderByPaidAtDesc(Long distributorId);

    @Query("select coalesce(sum(p.amount), 0) from CommissionPayout p where p.distributorId = :distributorId")
    BigDecimal sumAmountByDistributorId(@Param("distributorId") Long distributorId);
}
