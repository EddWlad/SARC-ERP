package com.tidsec.sarc_bakend.modules.intermittency.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.BlockingDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de los lotes de importacion de intermitencias y sus filas. */
public interface IntermittencyImportRepository {

	IntermittencyImportBatch saveBatch(IntermittencyImportBatch batch);

	Optional<IntermittencyImportBatch> findBatch(UUID id);

	/** El lote mas reciente primero. */
	PageResult<IntermittencyImportBatch> searchBatches(PageQuery page);

	/** Lotes que quedaron en PARSING o CONFIRMING: tras un reinicio ningun trabajo los va a terminar. */
	List<IntermittencyImportBatch> findBatchesInProgress();

	void insertRows(List<IntermittencyImportRow> rows);

	IntermittencyImportRow saveRow(IntermittencyImportRow row);

	Optional<IntermittencyImportRow> findRow(UUID id);

	/** Todas las filas del lote en orden de fila: la vista previa se reclasifica completa. */
	List<IntermittencyImportRow> findRows(UUID batchId);

	/** Filas del lote cuyo punto resuelto esta entre estos: las que se comparan entre si al reclasificar. */
	List<IntermittencyImportRow> findRows(UUID batchId, java.util.Collection<UUID> equipmentIds);

	/** Conteos por clasificacion calculados en la base, para no cargar todas las filas despues de cada edicion. */
	com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowCounts countRows(UUID batchId);

	/** Sin clasificacion, todas las filas del lote. */
	PageResult<IntermittencyImportRow> searchRows(UUID batchId, RowClassification classification, PageQuery page);

	/**
	 * Filas KEEP_PENDING de lotes COMPLETED sin resolver que tienen algun dia dentro de {@code [from, to]}
	 * (ambos incluidos, nulos sin limite). {@code equipmentIds} vacio son todas; con puntos, solo las que ya
	 * tienen uno de ellos (una fila por vincular no tiene punto y queda fuera).
	 */
	PageResult<IntermittencyImportRow> searchPendingRows(LocalDate from, LocalDate to,
			RowClassification classification, java.util.Collection<UUID> equipmentIds, PageQuery page);

	/**
	 * Filas KEEP_PENDING de lotes COMPLETED sin resolver, por vincular o en conflicto, con algun dia dentro de
	 * {@code [from, to]} (ambos incluidos): son las que dejan sin cubrir un dia de la disponibilidad estimada
	 * (FR-045). Devuelve un dia por cada dia de la fila dentro del rango, sin cargar las filas ni sus JSON.
	 */
	List<BlockingDay> findBlockingDays(LocalDate from, LocalDate to);

	/** Lotes en vista previa (READY) con alguna fila que toca el dia: todavia no afectan la cobertura. */
	List<IntermittencyImportBatch> findUnconfirmedBatchesTouching(LocalDate day);

	/** Cantidad de lotes en vista previa que tocan cada dia de {@code [from, to]}; los dias sin lotes no aparecen. */
	java.util.Map<LocalDate, Integer> countUnconfirmedBatchesByDay(LocalDate from, LocalDate to);

	/** Cantidad de las filas de {@link #searchPendingRows}, sin cargarlas. */
	long countPendingRows(LocalDate from, LocalDate to, RowClassification classification,
			java.util.Collection<UUID> equipmentIds);
}
