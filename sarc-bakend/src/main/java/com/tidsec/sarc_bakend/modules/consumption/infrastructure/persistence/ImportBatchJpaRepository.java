package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportStatus;

interface ImportBatchJpaRepository extends JpaRepository<ImportBatch, UUID> {

	List<ImportBatch> findByStatusIn(Collection<ImportStatus> statuses);
}
