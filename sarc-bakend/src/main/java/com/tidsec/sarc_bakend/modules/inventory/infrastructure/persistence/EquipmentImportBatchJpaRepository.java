package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

interface EquipmentImportBatchJpaRepository extends JpaRepository<EquipmentImportBatch, UUID> {

	List<EquipmentImportBatch> findByStatusIn(Collection<ImportStatus> statuses);
}
