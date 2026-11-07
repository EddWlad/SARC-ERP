package com.tidsec.sarc_bakend.modules.uptime.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.ConnectorAlias;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de lotes, filas y alias de uptime. */
public interface UptimeImportRepository {

    record Original(String reference,String checksum) {}
    Optional<Original> findOriginal(UUID batchId);
    List<UptimeImportRow> findProvenanceRows(UUID measurementId,UUID batchId);

	UptimeImportBatch saveBatch(UptimeImportBatch batch);

	Optional<UptimeImportBatch> findBatch(UUID id);

	/** El lote mas reciente primero. */
	PageResult<UptimeImportBatch> searchBatches(PageQuery page);

	List<UptimeImportBatch> findBatchesInProgress();

	void insertRows(List<UptimeImportRow> rows);

	Optional<UptimeImportRow> findRow(UUID id);

	List<UptimeImportRow> findRows(UUID batchId, Collection<UUID> ids);

	/** Sin clasificacion, todas las filas del lote. */
	PageResult<UptimeImportRow> searchRows(UUID batchId, UptimeRowClassification classification, PageQuery page);

	/** Siguientes filas sin resultado, en orden: reanudar continua donde quedo. */
	List<UptimeImportRow> nextToApply(UUID batchId, int limit);

	int countApplied(UUID batchId);

	PageResult<UptimeImportRow> searchPendingLinks(PageQuery page);

	List<ConnectorAlias> findAliases(MeasurementSource source, Collection<UUID> equipmentIds);

	Optional<ConnectorAlias> findAlias(UUID id);

	Optional<ConnectorAlias> findAlias(MeasurementSource source, UUID equipmentId, String labelKey);

	ConnectorAlias saveAlias(ConnectorAlias alias);

	/** Sin equipo, todos; ordenados por etiqueta. */
	PageResult<ConnectorAlias> searchAliases(UUID equipmentId, PageQuery page);
    boolean hasRowsWithoutKnownRange(UUID batchId);
}
