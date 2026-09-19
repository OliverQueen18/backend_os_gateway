package com.osgateway.gatewaydevice.infrastructure.persistence;

import com.osgateway.gatewaydevice.domain.GatewayStatusSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayStatusRepository extends JpaRepository<GatewayStatusSnapshot, Long> {}