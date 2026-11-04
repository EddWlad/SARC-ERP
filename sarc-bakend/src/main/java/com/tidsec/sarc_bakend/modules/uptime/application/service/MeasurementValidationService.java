package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.uptime.application.result.BatchValidationResult;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementExclusion;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReviewState;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/** Validacion, exclusion y restauracion de mediciones, siempre sobre la version leida. */
@Service
@PreAuthorize(UptimePermissions.EDITORS)
public class MeasurementValidationService {

	public record Item(UUID id, long version) {
	}

	private final MeasurementRepository measurements;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;
	private final TransactionTemplate transactions;

	MeasurementValidationService(MeasurementRepository measurements, AuditTrail audit, CurrentActor actor, Clock clock,
			TransactionTemplate transactions) {
		this.measurements = measurements;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
		this.transactions = transactions;
	}

	@Transactional
	public MeasurementView validate(UUID id, long version, String justification) {
		Measurement measurement = found(id);
		measurement.requireVersion(version);
		return apply(measurement, justification);
	}

	/** Cada item corre en su transaccion: una version obsoleta no revierte los validados. */
	public BatchValidationResult validateBatch(List<Item> items) {
		List<BatchValidationResult.Skipped> skipped = new ArrayList<>();
		int validated = 0;
		for (Item item : items) {
			Optional<BatchValidationResult.Reason> reason;
			try {
				reason = transactions.execute(status -> validateItem(item));
			}
			catch (ObjectOptimisticLockingFailureException stale) {
				reason = Optional.of(BatchValidationResult.Reason.STALE_VERSION);
			}
			if (reason.isPresent()) {
				skipped.add(new BatchValidationResult.Skipped(item.id(), reason.get()));
			}
			else {
				validated++;
			}
		}
		audit(validated, skipped.size());
		return new BatchValidationResult(validated, skipped);
	}

	private void audit(int validated, int skipped) {
		if (validated > 0) {
			transactions.executeWithoutResult(status -> audit.record("UPTIME_BATCH_VALIDATED", "UPTIME_MEASUREMENT", null,
					Map.of("validated", Integer.toString(validated), "skipped", Integer.toString(skipped))));
		}
	}

	private Optional<BatchValidationResult.Reason> validateItem(Item item) {
		Measurement measurement = measurements.findById(item.id()).orElse(null);
		if (measurement == null) {
			return Optional.of(BatchValidationResult.Reason.NOT_FOUND);
		}
		if (measurement.getVersion() != item.version()) {
			return Optional.of(BatchValidationResult.Reason.STALE_VERSION);
		}
		MeasurementRevision latest = latest(measurement);
		if (latest.getReviewState() != ReviewState.PENDING) {
			return Optional.of(BatchValidationResult.Reason.NOT_PENDING);
		}
		if (!latest.getObservations().isEmpty()) {
			return Optional.of(BatchValidationResult.Reason.OBSERVED);
		}
		apply(measurement, null);
		return Optional.empty();
	}

	private MeasurementView apply(Measurement measurement, String justification) {
		MeasurementRevision latest = latest(measurement);
		MeasurementRevision effective = measurement.getEffectiveRevision() == null ? null
				: measurements.findRevision(measurement.getId(), measurement.getEffectiveRevision()).orElseThrow();
		measurement.validate(latest, effective, actor.id().orElse(null), clock.instant(), blankToNull(justification));
		measurements.save(measurement);
		audit.record("UPTIME_MEASUREMENT_VALIDATED", "UPTIME_MEASUREMENT", measurement.getId(),
				Map.of("revision", Integer.toString(latest.getRevision())));
		return new MeasurementView(measurement, latest);
	}

	@Transactional
	public MeasurementView exclude(UUID id, long version, String justification) {
		Measurement measurement = found(id);
		measurement.requireVersion(version);
		measurement.exclude();
		return record(measurement, MeasurementExclusion.Action.EXCLUDED, justification, "UPTIME_MEASUREMENT_EXCLUDED");
	}

	@Transactional
	public MeasurementView restore(UUID id, long version, String justification) {
		Measurement measurement = found(id);
		measurement.requireVersion(version);
		measurement.restore();
		return record(measurement, MeasurementExclusion.Action.RESTORED, justification, "UPTIME_MEASUREMENT_RESTORED");
	}

	private MeasurementView record(Measurement measurement, MeasurementExclusion.Action action, String justification,
			String auditAction) {
		Measurement saved = measurements.save(measurement);
		Instant now = clock.instant();
		measurements.saveExclusion(new MeasurementExclusion(saved.getId(), action, justification.strip(),
				saved.getVersion(), actor.id().orElse(null), now));
		audit.record(auditAction, "UPTIME_MEASUREMENT", saved.getId(), Map.of("excluded",
				Boolean.toString(saved.isExcluded())));
		return new MeasurementView(saved, latest(saved));
	}

	private Measurement found(UUID id) {
		return measurements.findById(id)
				.orElseThrow(() -> new NotFoundException("UPTIME_MEASUREMENT_NOT_FOUND", "Medicion no encontrada."));
	}

	private MeasurementRevision latest(Measurement measurement) {
		return measurements.findRevision(measurement.getId(), measurement.getLatestRevision()).orElseThrow();
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
