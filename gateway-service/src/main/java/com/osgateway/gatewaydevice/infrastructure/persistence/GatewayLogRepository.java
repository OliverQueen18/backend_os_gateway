package com.osgateway.gatewaydevice.infrastructure.persistence;

import com.osgateway.gatewaydevice.domain.GatewayLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayLogRepository extends JpaRepository<GatewayLog, Long> {}