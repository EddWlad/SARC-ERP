package com.tidsec.sarc_bakend.modules.uptime.domain.repository;

import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementPeriod;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementExclusion;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de las mediciones. */
public interface MeasurementRepository {

	/** Escribe de inmediato: la unicidad de identidad debe fallar aqui, no al confirmar. */
	Measurement save(Measurement measurement);
    Measurement saveTemporalDecision(Measurement measurement);
    List<Measurement> findTemporalCandidates(UUID connectorId,MeasurementSource source);

	MeasurementRevision saveRevision(MeasurementRevision revision);

	Optional<Measurement> findById(UUID id);

	Optional<Measurement> findByIdentity(UUID connectorId, MeasurementSource source, Instant periodStart,
			Instant periodEnd);

    Optional<Measurement> findByReference(UUID connectorId, MeasurementSource source, LocalDate week);
    default Optional<Measurement> findByIdentity(UUID connectorId, MeasurementSource source, MeasurementPeriod period) {
        return switch(period.precision()) {
            case KNOWN_RANGE -> findByIdentity(connectorId,source,period.start(),period.end());
            case REFERENCE_WEEK -> findByReference(connectorId,source,period.referenceWeek());
            case REQUIRES_RECONCILIATION -> Optional.empty();
        };
    }

    List<Measurement> findByReferenceWeek(Collection<UUID> connectorIds,MeasurementSource source,
        LocalDate referenceWeek,TemporalPrecision precision);

	Optional<MeasurementRevision> findRevision(UUID measurementId, int revision);

	List<MeasurementRevision> findRevisions(UUID measurementId);

	void saveExclusion(MeasurementExclusion exclusion);

	List<MeasurementExclusion> findExclusions(UUID measurementId);

	/** Ordenadas por fin de periodo descendente. */
	PageResult<Measurement> search(MeasurementCriteria criteria, PageQuery page);

	/** Mediciones de esa identidad exacta para varios conectores. */
	List<Measurement> findByPeriod(Collection<UUID> connectorIds, MeasurementSource source, Instant periodStart,
			Instant periodEnd);

	/** Lecturas con fin de periodo en {@code [from, to]}. */
	List<MeasurementFact> findFacts(Instant from, Instant to);

    List<MeasurementFact> findReferenceFacts(LocalDate week, Collection<UUID> connectorIds);
    Optional<LocalDate> latestIncludedReference();
    long countPendingLinksByReference(LocalDate week, TemporalPrecision section);

	Instant lastValidatedAt();

	/** Filas sin atribuir por fin de periodo; ignora red, equipo y conector. Sin rango, todas. */
	long countPendingLinks(Instant from, Instant to);

	/** Ultima revision de cada medicion. */
	List<MeasurementRevision> findLatestRevisions(Collection<Measurement> measurements);
}
