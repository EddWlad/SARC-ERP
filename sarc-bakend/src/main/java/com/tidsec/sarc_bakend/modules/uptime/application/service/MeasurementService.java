package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.uptime.application.command.ClassifyCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.command.CorrectMeasurementCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.command.RegisterMeasurementCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.port.MeasurementBlockParser;
import com.tidsec.sarc_bakend.modules.uptime.application.result.ClassificationResult;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementView;
import com.tidsec.sarc_bakend.modules.uptime.application.result.RegistrationResult;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementOrigin;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.RevisionData;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.PeriodRules;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeScope;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Alta de mediciones. Sin transaccion de clase: una alta concurrente rompe la unicidad y aborta su
 * transaccion, de modo que el reconocimiento de la existente corre en otra.
 */
@Service
@PreAuthorize(UptimePermissions.EDITORS)
public class MeasurementService {

	private final MeasurementRepository measurements;
	private final ConnectorDirectory connectors;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final OperatingZone zone;
	private final Clock clock;
	private final TransactionTemplate transactions;
	private final MeasurementBlockParser blockParser;

	MeasurementService(MeasurementRepository measurements, ConnectorDirectory connectors, AuditTrail audit,
			CurrentActor actor, OperatingZone zone, Clock clock, TransactionTemplate transactions,
			MeasurementBlockParser blockParser) {
		this.measurements = measurements;
		this.connectors = connectors;
		this.audit = audit;
		this.actor = actor;
		this.zone = zone;
		this.clock = clock;
		this.transactions = transactions;
		this.blockParser = blockParser;
	}

	/** Interpreta sin guardar: la persona revisa los valores antes de registrar. */
	public MeasurementBlockParser.Block parseBlock(String text) {
		return blockParser.parse(text);
	}

	public RegistrationResult register(RegisterMeasurementCommand command) {
		try {
			return transactions.execute(status -> registerInTransaction(command));
		}
		catch (DataIntegrityViolationException concurrent) {
			return transactions.execute(status -> recognize(command));
		}
	}

	/** Nueva revision; copia la etiqueta de la fecha del hecho y deja intacta la vigente. */
	public MeasurementView correct(UUID id, CorrectMeasurementCommand command) {
		return transactions.execute(status -> correctInTransaction(id, command));
	}

	private MeasurementView correctInTransaction(UUID id, CorrectMeasurementCommand command) {
		Measurement measurement = measurements.findById(id)
				.orElseThrow(() -> new NotFoundException("UPTIME_MEASUREMENT_NOT_FOUND", "Medicion no encontrada."));
		measurement.requireVersion(command.version());
		Instant now = clock.instant();
		PeriodRules.check(measurement.getPeriodStart(), measurement.getPeriodEnd(), command.periodType(),
				command.exactConfirmed(), now, zone.zone());
		MeasurementRevision latest = measurements.findRevision(id, measurement.getLatestRevision()).orElseThrow();
		RevisionData data = correctionData(command, measurement, latest, now);
		MeasurementRevision revision = measurement.correct(data, latest, now);
		measurements.save(measurement);
		measurements.saveRevision(revision);
		audit.record("UPTIME_MEASUREMENT_CORRECTED", "UPTIME_MEASUREMENT", id,
				Map.of("previous", summary(latest), "current", summary(revision)));
		return new MeasurementView(measurement, revision);
	}

	private RevisionData correctionData(CorrectMeasurementCommand command, Measurement measurement,
			MeasurementRevision latest, Instant now) {
		UUID author = actor.id().orElse(null);
		UUID confirmedBy = command.exactConfirmed() ? author : null;
		Instant confirmedAt = command.exactConfirmed() ? now : null;
		if (command.readingClass() != ReadingClass.MEASURED) {
			return new RevisionData(command.readingClass(), command.periodType(), null, null, null, null, null, List.of(),
					command.justification(), MeasurementOrigin.MANUAL, null, null, latest.getEquipmentLabel(),
					latest.getEquipmentCode(), latest.getConnectorTypeLabel(), confirmedBy, confirmedAt, author, now,
					UptimeBasis.STATES);
		}
		// Un informe semanal sin Ocupado se corrige igual: sin Ocupado sigue valiendo el uptime informado.
		UptimeBasis basis = latest.getUptimeBasis() == UptimeBasis.REPORTED_PERCENT && command.occupiedMinutes() == null
				? UptimeBasis.REPORTED_PERCENT : UptimeBasis.STATES;
		requireAllMinutes(command, basis);
		// Sin un porcentaje nuevo, la correccion conserva el que informo la fuente.
		BigDecimal reported = basis == UptimeBasis.REPORTED_PERCENT && command.reportedPercent() == null
				? latest.getReportedPercent() : command.reportedPercent();
		MeasurementRules.Assessment assessment = MeasurementRules.assess(
				new MeasurementRules.Values(command.availableMinutes(), command.occupiedMinutes(),
						command.faultMinutes(), command.offlineMinutes(), reported, basis),
				PeriodRules.durationMinutes(measurement.getPeriodStart(), measurement.getPeriodEnd()),
				command.periodType());
		return new RevisionData(ReadingClass.MEASURED, command.periodType(), assessment.available(),
				assessment.occupied(), assessment.fault(), assessment.offline(), reported,
				assessment.observations(), command.justification(), MeasurementOrigin.MANUAL, null,
				sourceValues(command.availableMinutes(), command.occupiedMinutes(), command.faultMinutes(),
						command.offlineMinutes(), reported, null),
				latest.getEquipmentLabel(), latest.getEquipmentCode(), latest.getConnectorTypeLabel(), confirmedBy,
				confirmedAt, author, now, assessment.basis());
	}

	private static void requireAllMinutes(CorrectMeasurementCommand command, UptimeBasis basis) {
		boolean occupiedMissing = basis == UptimeBasis.STATES && command.occupiedMinutes() == null;
		if (command.availableMinutes() == null || occupiedMissing || command.faultMinutes() == null
				|| command.offlineMinutes() == null) {
			throw new BusinessRuleException("UPTIME_MINUTES_REQUIRED", basis == UptimeBasis.STATES
					? "Ingresa los cuatro tiempos." : "Ingresa Disponible, Con falla y Fuera de línea.");
		}
	}

	private static String summary(MeasurementRevision revision) {
		return "%s %s/%s/%s/%s".formatted(revision.getReadingClass(), revision.getAvailableMinutes(),
				revision.getOccupiedMinutes(), revision.getFaultMinutes(), revision.getOfflineMinutes());
	}

	/** Marca especial por conector. Una medicion distinta se informa, nunca se sobrescribe. */
	public List<ClassificationResult> classify(ClassifyCommand command) {
		if (command.readingClass() == ReadingClass.MEASURED) {
			throw new BusinessRuleException("UPTIME_CLASS_INVALID", "Elige Sin datos, Fuera de alcance o Retirado.");
		}
		return transactions.execute(status -> {
			Instant now = clock.instant();
			PeriodRules.check(command.periodStart(), command.periodEnd(), command.periodType(), command.exactConfirmed(),
					now, zone.zone());
			return targets(command).stream().map(connector -> classifyOne(command, connector, now)).toList();
		});
	}

	private List<MeasurableConnector> targets(ClassifyCommand command) {
		if (command.connectorId() != null) {
			return List.of(measurable(command.connectorId()));
		}
		return connectors.search(new ConnectorQuery(null, null, null, null, command.equipmentId())).stream()
				.filter(connector -> UptimeScope.includes(connector.network(), connector.type()))
				.filter(MeasurableConnector::connectorActive).toList();
	}

	private ClassificationResult classifyOne(ClassifyCommand command, MeasurableConnector connector, Instant now) {
		UUID author = actor.id().orElse(null);
		RevisionData data = new RevisionData(command.readingClass(), command.periodType(), null, null, null, null, null,
				List.of(), command.justification(), MeasurementOrigin.MANUAL, null, null, connector.equipmentName(),
				connector.assignedCode(), connector.connectorTypeName(), command.exactConfirmed() ? author : null,
				command.exactConfirmed() ? now : null, author, now, UptimeBasis.STATES);
		var existing = measurements.findByIdentity(connector.connectorId(), command.source(), command.periodStart(),
				command.periodEnd());
		if (existing.isEmpty()) {
			Measurement measurement = Measurement.open(connector.connectorId(), connector.equipmentId(), command.source(),
					command.periodStart(), command.periodEnd(), zone.zone().getId());
			MeasurementRevision revision = measurement.register(data);
			measurements.save(measurement);
			measurements.saveRevision(revision);
			audit.record("UPTIME_MEASUREMENT_CREATED", "UPTIME_MEASUREMENT", measurement.getId(),
					Map.of("connectorId", connector.connectorId().toString(), "readingClass",
							command.readingClass().name()));
			return new ClassificationResult(connector.connectorId(), ClassificationResult.Outcome.CREATED,
					measurement.getId());
		}
		Measurement measurement = existing.get();
		MeasurementRevision latest = measurements.findRevision(measurement.getId(), measurement.getLatestRevision())
				.orElseThrow();
		return new ClassificationResult(connector.connectorId(), latest.sameReading(data)
				? ClassificationResult.Outcome.IDENTICAL : ClassificationResult.Outcome.DIFFERS, measurement.getId());
	}

	private RegistrationResult registerInTransaction(RegisterMeasurementCommand command) {
		Instant now = clock.instant();
		PeriodRules.check(command.periodStart(), command.periodEnd(), command.periodType(), command.exactConfirmed(),
				now, zone.zone());
		MeasurableConnector connector = measurable(command.connectorId());
		RevisionData data = revisionData(command, connector, now);
		return measurements.findByIdentity(command.connectorId(), command.source(), command.periodStart(),
				command.periodEnd()).map(existing -> compare(existing, data))
				.orElseGet(() -> create(command, connector, data));
	}

	private RegistrationResult recognize(RegisterMeasurementCommand command) {
		MeasurableConnector connector = measurable(command.connectorId());
		RevisionData data = revisionData(command, connector, clock.instant());
		Measurement existing = measurements.findByIdentity(command.connectorId(), command.source(),
				command.periodStart(), command.periodEnd()).orElseThrow(() -> new ConflictException(
						"DATA_INTEGRITY_VIOLATION", "La medición no se pudo registrar."));
		return compare(existing, data);
	}

	private RegistrationResult create(RegisterMeasurementCommand command, MeasurableConnector connector,
			RevisionData data) {
		Measurement measurement = Measurement.open(connector.connectorId(), connector.equipmentId(), command.source(),
				command.periodStart(), command.periodEnd(), zone.zone().getId());
		MeasurementRevision revision = measurement.register(data);
		measurements.save(measurement);
		measurements.saveRevision(revision);
		audit.record("UPTIME_MEASUREMENT_CREATED", "UPTIME_MEASUREMENT", measurement.getId(),
				Map.of("connectorId", connector.connectorId().toString(), "periodType", data.periodType().name(),
						"observations", data.observations().toString()));
		return new RegistrationResult(RegistrationResult.Outcome.CREATED, new MeasurementView(measurement, revision));
	}

	private RegistrationResult compare(Measurement existing, RevisionData data) {
		MeasurementRevision latest = measurements.findRevision(existing.getId(), existing.getLatestRevision())
				.orElseThrow();
		if (latest.sameReading(data)) {
			return new RegistrationResult(RegistrationResult.Outcome.IDENTICAL, new MeasurementView(existing, latest));
		}
		throw new ConflictException("UPTIME_MEASUREMENT_DIFFERS",
				"Ya existe una medición distinta para ese conector y período.",
				Map.of("id", existing.getId(), "version", existing.getVersion()));
	}

	private MeasurableConnector measurable(UUID connectorId) {
		MeasurableConnector connector = connectors.findByIds(List.of(connectorId)).get(connectorId);
		if (connector == null || connector.equipmentStatus() == AdministrativeStatus.DELETED) {
			throw new BusinessRuleException("UPTIME_CONNECTOR_NOT_MEASURABLE", "El conector no se puede medir.");
		}
		if (!UptimeScope.includes(connector.network(), connector.type())) {
			throw new BusinessRuleException("UPTIME_OUT_OF_SCOPE", "Esta infraestructura no se evalua en uptime.");
		}
		return connector;
	}

	private RevisionData revisionData(RegisterMeasurementCommand command, MeasurableConnector connector, Instant now) {
		MeasurementRules.Assessment assessment = MeasurementRules.assess(
				new MeasurementRules.Values(command.availableMinutes(), command.occupiedMinutes(),
						command.faultMinutes(), command.offlineMinutes(), command.reportedPercent()),
				PeriodRules.durationMinutes(command.periodStart(), command.periodEnd()), command.periodType());
		UUID author = actor.id().orElse(null);
		return new RevisionData(ReadingClass.MEASURED, command.periodType(), assessment.available(),
				assessment.occupied(), assessment.fault(), assessment.offline(), command.reportedPercent(),
				assessment.observations(), null, command.origin(), null,
				sourceValues(command.availableMinutes(), command.occupiedMinutes(), command.faultMinutes(),
						command.offlineMinutes(), command.reportedPercent(), command.sourceText()),
				connector.equipmentName(), connector.assignedCode(), connector.connectorTypeName(),
				command.exactConfirmed() ? author : null, command.exactConfirmed() ? now : null, author, now,
				UptimeBasis.STATES);
	}

	/** Conserva lo digitado tal como llego, tambien en captura manual. */
	private static Map<String, Object> sourceValues(BigDecimal available, BigDecimal occupied, BigDecimal fault,
			BigDecimal offline, BigDecimal reportedPercent, String sourceText) {
		Map<String, Object> values = new HashMap<>();
		values.put("availableMinutes", available.toPlainString());
		if (occupied != null) {
			values.put("occupiedMinutes", occupied.toPlainString());
		}
		values.put("faultMinutes", fault.toPlainString());
		values.put("offlineMinutes", offline.toPlainString());
		if (reportedPercent != null) {
			values.put("reportedPercent", reportedPercent.toPlainString());
		}
		if (sourceText != null) {
			values.put("sourceText", sourceText);
		}
		return values;
	}
}
