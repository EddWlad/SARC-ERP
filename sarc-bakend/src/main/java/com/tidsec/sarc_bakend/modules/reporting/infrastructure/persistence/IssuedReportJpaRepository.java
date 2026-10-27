package com.tidsec.sarc_bakend.modules.reporting.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;

interface IssuedReportJpaRepository extends JpaRepository<IssuedReport, UUID> {

	Page<IssuedReport> findByKind(String kind, Pageable pageable);
}
