package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDayEvent;

interface ReviewedDayEventJpaRepository extends JpaRepository<ReviewedDayEvent, UUID> {
}
