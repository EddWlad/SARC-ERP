package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de las filas de una carga masiva de equipos. */
public interface EquipmentImportRowRepository {

	/** Guarda las filas nuevas de una vista previa. */
	void insertAll(List<EquipmentImportRow> rows);

	List<EquipmentImportRow> findByIds(UUID batchId, Collection<UUID> ids);

	/** Filas del lote en orden; {@code classification} nula = todas. */
	PageResult<EquipmentImportRow> search(UUID batchId, EquipmentRowClassification classification,
			PageQuery pageQuery);

	/**
	 * Siguientes filas por aplicar: sin resultado registrado, que escriben en el inventario y, si lo
	 * requieren, aprobadas. En orden de fila, para que reanudar continue donde quedo.
	 */
	List<EquipmentImportRow> nextToApply(UUID batchId, int limit);

	/** Las que se habrian aplicado pero no fueron aprobadas quedan como omitidas. */
	void markUnapprovedAsSkipped(UUID batchId);

	/** Filas que crearon o actualizaron un equipo. */
	int countWritten(UUID batchId);
}
