package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/**
 * Puerto de persistencia de las sesiones. Los agregados se calculan en la base con las mismas
 * condiciones para dashboard, PDF y Excel (CA-003-05: mismos filtros, mismos totales).
 */
public interface EnergySessionRepository {

	EnergySession save(EnergySession session);

	void flushEnergyDecision(EnergySession session);

	void saveRevision(SessionRevision revision);

	/** Versiones anteriores de una sesion, de la mas reciente a la mas antigua. */
	List<SessionRevision> history(UUID sessionId);

	Optional<EnergySession> findById(UUID id);

	Optional<EnergySession> findByKey(SessionSource source, String scope, String externalSessionId);

	List<EnergySession> findByKeys(SessionSource source, String scope, Collection<String> externalSessionIds);

	/** Ordenadas por inicio descendente. */
	PageResult<EnergySession> search(SessionCriteria criteria, PageQuery pageQuery);

	/** Ordenadas por inicio; para reportes. */
	List<EnergySession> findAll(SessionCriteria criteria);

	long count(SessionCriteria criteria);

	/** Grupos por dia, red, plataforma, tipo, equipo y revision; el calculador deriva de ellos cada indicador. */
	List<SessionAggregate> aggregate(SessionCriteria criteria);

	/** Totales de los estados pedidos; solo los estados con sesiones. */
	Map<SessionState, EnergyTotals> totalsByState(SessionCriteria criteria, Collection<SessionState> states);

	/** Todos los equipos con energia conocida; el calculador aplica posiciones y empates. */
	List<EquipmentEnergy> byEquipment(SessionCriteria criteria);

	/** Pendientes de vincular en orden de ID, a partir de {@code afterId} (exclusivo, null = desde el inicio). */
	List<EnergySession> findPendingLink(UUID afterId, int limit);
}
