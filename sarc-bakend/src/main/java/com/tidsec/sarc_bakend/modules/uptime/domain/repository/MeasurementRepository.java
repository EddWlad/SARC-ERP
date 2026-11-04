package com.tidsec.sarc_bakend.modules.uptime.domain.repository;

import java.time.Instant;
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

	MeasurementRevision saveRevision(MeasurementRevision revision);

	Optional<Measurement> findById(UUID id);

	Optional<Measurement> findByIdentity(UUID connectorId, MeasurementSource source, Instant periodStart,
			Instant periodEnd);

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

	Instant lastValidatedAt();

	/** Filas sin atribuir por fin de periodo; ignora red, equipo y conector. Sin rango, todas. */
	long countPendingLinks(Instant from, Instant to);

	/** Ultima revision de cada medicion. */
	List<MeasurementRevision> findLatestRevisions(Collection<Measurement> measurements);
}
