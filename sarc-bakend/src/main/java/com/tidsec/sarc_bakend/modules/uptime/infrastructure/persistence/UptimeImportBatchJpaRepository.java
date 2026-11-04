package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

interface UptimeImportBatchJpaRepository extends JpaRepository<UptimeImportBatch, UUID> {

	List<UptimeImportBatch> findByStatusIn(Collection<ImportStatus> statuses);
}
