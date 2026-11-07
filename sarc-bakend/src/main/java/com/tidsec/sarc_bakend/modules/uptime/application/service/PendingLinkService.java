package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowOutcome;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.RevisionData;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeScope;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Concilia filas sin conector, una por una. Una medicion existente distinta no se toca: la fila queda
 * clasificada como conflicto y la correccion sigue su camino auditado.
 */
@Service
@Transactional
@PreAuthorize(UptimePermissions.EDITORS)
public class PendingLinkService {

	private final UptimeImportRepository imports;
	private final MeasurementRepository measurements;
	private final ConnectorDirectory connectors;
	private final ConnectorAliasService aliases;
	private final ImportedMeasurements imported;
	private final AuditTrail audit;
	private final CurrentActor actor;

	PendingLinkService(UptimeImportRepository imports, MeasurementRepository measurements,
			ConnectorDirectory connectors, ConnectorAliasService aliases, ImportedMeasurements imported, AuditTrail audit,
			CurrentActor actor) {
		this.imports = imports;
		this.measurements = measurements;
		this.connectors = connectors;
		this.aliases = aliases;
		this.imported = imported;
		this.audit = audit;
		this.actor = actor;
	}

	@Transactional(readOnly = true)
	public PageResult<UptimeImportRow> pending(PageQuery page) {
		return imports.searchPendingLinks(page);
	}

	public UptimeImportRow link(UUID rowId, long version, UUID connectorId, boolean rememberLabel) {
		UptimeImportRow row = imports.findRow(rowId)
				.orElseThrow(() -> new NotFoundException("IMPORT_ROW_NOT_FOUND", "La fila no existe."));
		row.requireVersion(version);
		row.requirePendingLink();
		MeasurableConnector connector = measurable(connectorId);
		if (row.getEquipmentId() != null && !row.getEquipmentId().equals(connector.equipmentId())) {
			throw new BusinessRuleException("UPTIME_LINK_EQUIPMENT_MISMATCH",
					"El conector no pertenece al equipo identificado.");
		}
		UptimeImportBatch batch = imports.findBatch(row.getBatchId()).orElseThrow();
		if (rememberLabel) {
			remember(batch, row, connector);
		}
		attach(batch, row, connector);
		audit.record("UPTIME_MEASUREMENT_LINKED", UptimeImportBatch.AUDIT_ENTITY_TYPE, batch.getId(),
				Map.of("rowId", rowId.toString(), "connectorId", connectorId.toString(), "classification",
						row.getClassification().name()));
		return row;
	}

	private void attach(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector) {
		RevisionData data = imported.data(batch, row, connector, actor.id().orElse(null), null);
		Optional<Measurement> existing = measurements.findByIdentity(connector.connectorId(), batch.getSource(),
				row.getPeriod("America/Guayaquil"));
		if (existing.isEmpty()) {
			row.classify(UptimeRowClassification.NEW, connector.equipmentId(), connector.connectorId(), null, null,
					List.of());
			row.recordOutcome(UptimeRowOutcome.LINKED, imported.create(batch, row, connector, data).getId());
			return;
		}
		Measurement measurement = existing.get();
		MeasurementRevision latest = measurements.findRevision(measurement.getId(), measurement.getLatestRevision())
				.orElseThrow();
		boolean same = latest.sameReading(data);
		row.classify(same ? UptimeRowClassification.IDENTICAL : UptimeRowClassification.CONFLICT,
				connector.equipmentId(), connector.connectorId(), measurement.getId(), latest.getRevision(),
				same ? List.of() : List.of(new RowIssue("UPTIME_MEASUREMENT_DIFFERS",
						"Ya existe una medición distinta para ese conector y período.")));
		row.recordOutcome(UptimeRowOutcome.SKIPPED, measurement.getId());
	}

	private void remember(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector) {
		if (row.getEquipmentId() == null) {
			throw new BusinessRuleException("UPTIME_ALIAS_EQUIPMENT_UNRESOLVED",
					"Solo se aprueba una etiqueta cuando el equipo esta identificado.");
		}
		if (row.getSourceConnector() == null || row.getSourceConnector().isBlank()) {
			throw new BusinessRuleException("UPTIME_ALIAS_LABEL_REQUIRED", "La fila no trae etiqueta de conector.");
		}
		aliases.remember(batch.getSource(), row.getEquipmentId(), connector.connectorId(), row.getSourceConnector());
	}

	private MeasurableConnector measurable(UUID connectorId) {
		MeasurableConnector connector = connectors.findByIds(List.of(connectorId)).get(connectorId);
		if (connector == null || connector.equipmentStatus() == AdministrativeStatus.DELETED
				|| !connector.connectorActive()) {
			throw new BusinessRuleException("UPTIME_CONNECTOR_NOT_MEASURABLE", "El conector no se puede medir.");
		}
		if (!UptimeScope.includes(connector.network(), connector.type())) {
			throw new BusinessRuleException("UPTIME_OUT_OF_SCOPE", "Esta infraestructura no se evalua en uptime.");
		}
		return connector;
	}
}
