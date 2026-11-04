package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementExclusion;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementCriteria;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class MeasurementRepositoryAdapter implements MeasurementRepository {

	/** Se mapea con JDBC: un ano de historial son cientos de miles de filas y una proyeccion JPA las encarece. */
	private static final String FACTS = """
			SELECT m.id, m.connector_id, m.equipment_id, m.period_start, m.period_end, r.period_type,
			       r.reading_class, m.effective_revision IS NOT NULL, m.excluded,
			       CASE WHEN r.uptime_basis = 'REPORTED_PERCENT'
			            THEN EXTRACT(EPOCH FROM m.period_end - m.period_start)::bigint / 60
			            ELSE COALESCE(r.available_minutes + r.occupied_minutes + r.fault_minutes + r.offline_minutes, 0)
			       END,
			       COALESCE(r.offline_minutes, 0), COALESCE(r.fault_minutes, 0),
			       l.review_state = 'PENDING', jsonb_array_length(l.observations) > 0,
			       r.uptime_basis, r.reported_percent
			FROM upt_measurement m
			JOIN upt_measurement_revision r ON r.measurement_id = m.id
			 AND r.revision = COALESCE(m.effective_revision, m.latest_revision)
			JOIN upt_measurement_revision l ON l.measurement_id = m.id AND l.revision = m.latest_revision
			WHERE m.period_end >= ? AND m.period_end <= ?
			""";

	private final JdbcTemplate jdbc;
	private final MeasurementJpaRepository measurements;
	private final MeasurementRevisionJpaRepository revisions;
	private final MeasurementExclusionJpaRepository exclusions;

	MeasurementRepositoryAdapter(JdbcTemplate jdbc, MeasurementJpaRepository measurements, MeasurementRevisionJpaRepository revisions,
			MeasurementExclusionJpaRepository exclusions) {
		this.jdbc = jdbc;
		this.measurements = measurements;
		this.revisions = revisions;
		this.exclusions = exclusions;
	}

	@Override
	public Measurement save(Measurement measurement) {
		return measurements.saveAndFlush(measurement);
	}

	@Override
	public MeasurementRevision saveRevision(MeasurementRevision revision) {
		return revisions.saveAndFlush(revision);
	}

	@Override
	public Optional<Measurement> findById(UUID id) {
		return measurements.findById(id);
	}

	@Override
	public Optional<Measurement> findByIdentity(UUID connectorId, MeasurementSource source, Instant periodStart,
			Instant periodEnd) {
		return measurements.findByConnectorIdAndSourceAndPeriodStartAndPeriodEnd(connectorId, source, periodStart,
				periodEnd);
	}

	@Override
	public Optional<MeasurementRevision> findRevision(UUID measurementId, int revision) {
		return revisions.findByMeasurementIdAndRevision(measurementId, revision);
	}

	@Override
	public List<MeasurementRevision> findRevisions(UUID measurementId) {
		return revisions.findByMeasurementIdOrderByRevisionDesc(measurementId);
	}

	@Override
	public void saveExclusion(MeasurementExclusion exclusion) {
		exclusions.saveAndFlush(exclusion);
	}

	@Override
	public List<MeasurementExclusion> findExclusions(UUID measurementId) {
		return exclusions.findByMeasurementIdOrderByDecidedAtDesc(measurementId);
	}

	@Override
	public PageResult<Measurement> search(MeasurementCriteria criteria, PageQuery page) {
		Page<Measurement> found = measurements.search(criteria.connectorId(), criteria.equipmentId(),
				criteria.queue() == null ? null : criteria.queue().name(), criteria.onlyEffective(),
				PageRequest.of(page.page(), page.size()));
		return new PageResult<>(found.getContent(), found.getNumber(), found.getSize(), found.getTotalElements(),
				found.getTotalPages());
	}

	@Override
	public List<MeasurementFact> findFacts(Instant from, Instant to) {
		return jdbc.query(FACTS, (row, index) -> new MeasurementFact(row.getObject(1, UUID.class),
				row.getObject(2, UUID.class), row.getObject(3, UUID.class), row.getTimestamp(4).toInstant(),
				row.getTimestamp(5).toInstant(), PeriodType.valueOf(row.getString(6)),
				ReadingClass.valueOf(row.getString(7)), row.getBoolean(8), row.getBoolean(9), row.getLong(10),
				row.getLong(11), row.getLong(12), row.getBoolean(13), row.getBoolean(14),
				downMinutes(UptimeBasis.valueOf(row.getString(15)), row.getLong(10), row.getLong(11),
						row.getBigDecimal(16))),
				Timestamp.from(from), Timestamp.from(to));
	}

	/** Con uptime informado, el periodo pesa completo y descuenta lo que el porcentaje deja fuera. */
	private static BigDecimal downMinutes(UptimeBasis basis, long evaluated, long offline, BigDecimal reported) {
		if (basis == UptimeBasis.STATES || reported == null) {
			return BigDecimal.valueOf(offline);
		}
		return BigDecimal.valueOf(evaluated).multiply(BigDecimal.valueOf(100).subtract(reported))
				.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
	}

	@Override
	public Instant lastValidatedAt() {
		return measurements.lastValidatedAt();
	}

	@Override
	public long countPendingLinks(Instant from, Instant to) {
		return measurements.countPendingLinks(from, to);
	}

	@Override
	public List<Measurement> findByPeriod(Collection<UUID> connectorIds, MeasurementSource source, Instant periodStart,
			Instant periodEnd) {
		return connectorIds.isEmpty() ? List.of()
				: measurements.findByConnectorIdInAndSourceAndPeriodStartAndPeriodEnd(connectorIds, source, periodStart,
						periodEnd);
	}

	@Override
	public List<MeasurementRevision> findLatestRevisions(Collection<Measurement> found) {
		Map<UUID, Integer> latest = found.stream()
				.collect(Collectors.toMap(Measurement::getId, Measurement::getLatestRevision));
		if (latest.isEmpty()) {
			return List.of();
		}
		return revisions.findByMeasurementIdIn(latest.keySet()).stream()
				.filter(revision -> latest.get(revision.getMeasurementId()) == revision.getRevision()).toList();
	}
}
