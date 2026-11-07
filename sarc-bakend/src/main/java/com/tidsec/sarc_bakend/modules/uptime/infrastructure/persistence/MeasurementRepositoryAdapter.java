package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
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
               CASE WHEN m.period_precision <> 'KNOWN_RANGE' THEN NULL
                    WHEN r.uptime_basis = 'REPORTED_PERCENT'
                    THEN EXTRACT(EPOCH FROM m.period_end - m.period_start)::bigint / 60
                    ELSE COALESCE(r.available_minutes::bigint + r.occupied_minutes + r.fault_minutes + r.offline_minutes, 0)
               END,
               COALESCE(r.offline_minutes, 0), COALESCE(r.fault_minutes, 0),
               l.review_state = 'PENDING', jsonb_array_length(l.observations) > 0,
               r.uptime_basis, r.reported_percent, m.period_precision, m.reference_week,
               r.available_minutes, r.occupied_minutes, r.fault_minutes, r.offline_minutes,
               t.result_temporal->>'targetPrecision'
        FROM upt_measurement m
        JOIN upt_measurement_revision r ON r.measurement_id = m.id
         AND r.revision = COALESCE(m.effective_revision, m.latest_revision)
        JOIN upt_measurement_revision l ON l.measurement_id = m.id AND l.revision = m.latest_revision
        LEFT JOIN LATERAL (
            SELECT result_temporal FROM upt_measurement_temporal_change e
            WHERE e.measurement_id = m.id AND e.policy_version = '006-1'
            ORDER BY e.expected_measurement_version DESC, e.occurred_at DESC LIMIT 1
        ) t ON true
        """;

	@jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
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
    public List<Measurement> findTemporalCandidates(UUID connectorId,MeasurementSource source) {
        return measurements.findByConnectorIdAndSourceAndTemporalPrecision(connectorId,source,TemporalPrecision.REQUIRES_RECONCILIATION);
    }

    @Override
    public Measurement saveTemporalDecision(Measurement measurement) {
        Long before=measurement.getVersion();
        Measurement saved=measurements.saveAndFlush(measurement);
        if (java.util.Objects.equals(before,saved.getVersion()))
            entityManager.lock(saved,jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return saved;
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
		return measurements.findByConnectorIdAndSourceAndPeriodStartAndPeriodEndAndTemporalPrecision(connectorId, source, periodStart,
				periodEnd, TemporalPrecision.KNOWN_RANGE);
	}

    @Override
    public Optional<Measurement> findByReference(UUID connectorId,MeasurementSource source,LocalDate week) {
        return measurements.findByConnectorIdAndSourceAndReferenceWeekAndTemporalPrecision(
            connectorId,source,week,TemporalPrecision.REFERENCE_WEEK);
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
        return jdbc.query(FACTS + " WHERE m.period_precision = 'KNOWN_RANGE' AND m.period_end >= ? AND m.period_end <= ?",
            MeasurementRepositoryAdapter::fact, Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public List<MeasurementFact> findReferenceFacts(LocalDate week,Collection<UUID> connectorIds) {
        if (connectorIds.isEmpty()) return List.of();
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query(
            FACTS + " WHERE m.reference_week = :week AND m.connector_id IN (:ids)",
            Map.of("week",java.sql.Date.valueOf(week),"ids",connectorIds),MeasurementRepositoryAdapter::fact);
    }

    @Override
    public Optional<LocalDate> latestIncludedReference() {
        return Optional.ofNullable(jdbc.queryForObject("""
            SELECT max(m.reference_week) FROM upt_measurement m
            JOIN upt_measurement_revision r ON r.measurement_id=m.id AND r.revision=m.effective_revision
            WHERE NOT m.excluded AND r.reading_class='MEASURED' AND m.reference_week IS NOT NULL
            AND ((m.period_precision='REFERENCE_WEEK' AND r.reported_percent BETWEEN 0 AND 100)
              OR (m.period_precision='KNOWN_RANGE' AND r.period_type='ROLLING_7D'
                AND CASE WHEN r.uptime_basis='REPORTED_PERCENT' THEN m.period_end>m.period_start
                    ELSE (r.available_minutes::bigint+r.occupied_minutes+r.fault_minutes+r.offline_minutes)>0 END))
            AND NOT EXISTS (
                SELECT 1 FROM upt_measurement candidate
                JOIN LATERAL (SELECT e.result_temporal FROM upt_measurement_temporal_change e
                    WHERE e.measurement_id=candidate.id AND e.policy_version='006-1'
                    ORDER BY e.expected_measurement_version DESC,e.occurred_at DESC LIMIT 1) event ON true
                WHERE candidate.connector_id=m.connector_id AND candidate.reference_week=m.reference_week
                  AND candidate.period_precision='REQUIRES_RECONCILIATION' AND NOT candidate.excluded
                  AND event.result_temporal->>'targetPrecision'=m.period_precision)
            """,LocalDate.class));
    }

    @Override
    public long countPendingLinksByReference(LocalDate week,TemporalPrecision section) {
        if (week==null || section==null) return 0;
        return jdbc.queryForObject("""
            SELECT count(*) FROM upt_import_row WHERE outcome='PENDING_LINK'
            AND reference_week=? AND reference_period_type='ROLLING_7D' AND period_precision=?
            """,Long.class,java.sql.Date.valueOf(week),section.name());
    }

    private static MeasurementFact fact(java.sql.ResultSet row,int index) throws java.sql.SQLException {
        TemporalPrecision precision=TemporalPrecision.valueOf(row.getString(17));
        Long evaluated=number(row,10);
        String pending=row.getString(23);
        return new MeasurementFact(row.getObject(1,UUID.class),row.getObject(2,UUID.class),row.getObject(3,UUID.class),
            instant(row.getTimestamp(4)),instant(row.getTimestamp(5)),
            precision==TemporalPrecision.REFERENCE_WEEK?PeriodType.ROLLING_7D:PeriodType.valueOf(row.getString(6)),
            ReadingClass.valueOf(row.getString(7)),row.getBoolean(8),row.getBoolean(9),evaluated,
            precision==TemporalPrecision.KNOWN_RANGE?number(row,11):null,
            precision==TemporalPrecision.KNOWN_RANGE?number(row,12):null,
            row.getBoolean(13),row.getBoolean(14),evaluated==null?null:
                downMinutes(UptimeBasis.valueOf(row.getString(15)),evaluated,row.getLong(11),row.getBigDecimal(16)),
            precision,row.getObject(18,LocalDate.class),row.getBigDecimal(16),
            number(row,19),number(row,20),number(row,21),number(row,22),
            pending==null?null:TemporalPrecision.valueOf(pending));
    }

    private static Long number(java.sql.ResultSet row,int column) throws java.sql.SQLException {
        Number value=(Number)row.getObject(column);return value==null?null:value.longValue();
    }
    private static Instant instant(Timestamp value) {return value==null?null:value.toInstant();}

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
				: measurements.findByConnectorIdInAndSourceAndPeriodStartAndPeriodEndAndTemporalPrecision(connectorIds, source, periodStart,
						periodEnd, TemporalPrecision.KNOWN_RANGE);
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
    @Override
    public List<Measurement> findByReferenceWeek(Collection<UUID> ids,MeasurementSource source,LocalDate week,
            TemporalPrecision precision) {
        return ids.isEmpty()?List.of():measurements
            .findByConnectorIdInAndSourceAndReferenceWeekAndTemporalPrecisionOrderByPeriodEndDescPeriodStartDesc(ids,source,week,precision);
    }
}
