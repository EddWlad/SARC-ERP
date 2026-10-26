package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de los lotes de carga masiva de equipos. */
public interface EquipmentImportBatchRepository {

	EquipmentImportBatch save(EquipmentImportBatch batch);

	Optional<EquipmentImportBatch> findById(UUID id);

	/** Historial, el mas reciente primero. */
	PageResult<EquipmentImportBatch> search(PageQuery pageQuery);

	/** Lotes que quedaron interpretandose o confirmandose (p. ej. si la aplicacion se detuvo). */
	List<EquipmentImportBatch> findInProgress();
}
