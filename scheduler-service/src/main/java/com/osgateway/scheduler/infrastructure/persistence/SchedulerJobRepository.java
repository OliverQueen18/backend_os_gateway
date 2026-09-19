package com.osgateway.scheduler.infrastructure.persistence;

import com.osgateway.scheduler.domain.SchedulerJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerJobRepository extends JpaRepository<SchedulerJob, Long> {}