package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

public interface ImportRowRepository {

	/**
	 * Guarda las filas nuevas de una vista previa. Las ya guardadas no pasan por aqui: sus decisiones y
	 * resultados se escriben al confirmar la transaccion que las modifico.
	 */
	void insertAll(List<ImportRow> rows);

	List<ImportRow> findByIds(UUID batchId, Collection<UUID> ids);

	/** Filas del lote en orden; {@code classification} nula = todas. */
	PageResult<ImportRow> search(UUID batchId, RowClassification classification, PageQuery pageQuery);

	/**
	 * Siguientes filas por incorporar: sin resultado registrado, que escriben una sesion y, si la
	 * requieren, aprobadas. En orden de fila, para que reanudar continue donde quedo.
	 */
	List<ImportRow> nextToApply(UUID batchId, int limit);

	/** Las que se habrian incorporado pero no fueron aprobadas quedan como omitidas. */
	void markUnapprovedAsSkipped(UUID batchId);

	int countWritten(UUID batchId);
}
