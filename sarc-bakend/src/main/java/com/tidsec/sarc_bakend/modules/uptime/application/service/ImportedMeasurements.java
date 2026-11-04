package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementOrigin;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.RevisionData;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.PeriodRules;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Convierte una fila en medicion o revision. Lo comparten la confirmacion del lote y la vinculacion
 * posterior, para que ambas guarden lo mismo y comparen igual.
 */
@Component
class ImportedMeasurements {

	private final MeasurementRepository measurements;
	private final AuditTrail audit;
	private final OperatingZone zone;
	private final Clock clock;

	ImportedMeasurements(MeasurementRepository measurements, AuditTrail audit, OperatingZone zone, Clock clock) {
		this.measurements = measurements;
		this.audit = audit;
		this.zone = zone;
		this.clock = clock;
	}

	/** Solo las filas alineadas y sin observaciones aceptan la confirmacion exacta del lote. */
	boolean qualifiesAsExact(UptimeImportRow row) {
		return row.getObservations().isEmpty()
				&& PeriodRules.isAlignedWeek(row.getPeriodStart(), row.getPeriodEnd(), zone.zone());
	}

	RevisionData data(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector, UUID author,
			String justification) {
		boolean exact = batch.isExactConfirmed() && qualifiesAsExact(row);
		return data(batch, row, connector, exact ? PeriodType.EXACT_WEEK : row.getPeriodType(), exact, author,
				justification);
	}

	RevisionData data(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector, PeriodType type,
			boolean exact, UUID author, String justification) {
		// D12: el informe de Enerlink nombra el equipo en Cargador; Activo no siempre viene (T091).
		String label = row.getSourceCharger() == null || row.getSourceCharger().isBlank() ? connector.equipmentName()
				: row.getSourceCharger();
		return new RevisionData(ReadingClass.MEASURED, type, row.getAvailableMinutes(), row.getOccupiedMinutes(),
				row.getFaultMinutes(), row.getOfflineMinutes(), row.getReportedPercent(), row.getObservations(),
				justification, batch.getOrigin() == ImportOrigin.FILE ? MeasurementOrigin.FILE : MeasurementOrigin.PASTE,
				batch.getId(), row.getSourceValues(), label, connector.assignedCode(), connector.connectorTypeName(),
				exact ? batch.getExactConfirmedBy() : null, exact ? batch.getExactConfirmedAt() : null, author,
				clock.instant(), row.getUptimeBasis());
	}

	Measurement create(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector, RevisionData data) {
		Measurement measurement = Measurement.open(connector.connectorId(), connector.equipmentId(), batch.getSource(),
				row.getPeriodStart(), row.getPeriodEnd(), zone.zone().getId());
		MeasurementRevision revision = measurement.register(data);
		measurements.save(measurement);
		measurements.saveRevision(revision);
		audit.record("UPTIME_MEASUREMENT_CREATED", "UPTIME_MEASUREMENT", measurement.getId(),
				Map.of("connectorId", connector.connectorId().toString(), "batchId", batch.getId().toString(),
						"observations", data.observations().toString()));
		return measurement;
	}

	void correct(UptimeImportBatch batch, Measurement measurement, MeasurementRevision latest, RevisionData data) {
		Instant at = data.enteredAt();
		MeasurementRevision revision = measurement.correct(data, latest, at);
		measurements.save(measurement);
		measurements.saveRevision(revision);
		audit.record("UPTIME_MEASUREMENT_CORRECTED", "UPTIME_MEASUREMENT", measurement.getId(),
				Map.of("batchId", batch.getId().toString(), "revision", String.valueOf(revision.getRevision())));
	}
}
