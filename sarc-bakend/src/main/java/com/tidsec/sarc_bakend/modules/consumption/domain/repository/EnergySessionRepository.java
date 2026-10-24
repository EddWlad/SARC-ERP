package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/**
 * Puerto de persistencia de las sesiones. Los agregados se calculan en la base con las mismas
 * condiciones para dashboard, PDF y Excel (CA-003-05: mismos filtros, mismos totales).
 */
public interface EnergySessionRepository {

	EnergySession save(EnergySession session);

	void saveRevision(SessionRevision revision);

	Optional<EnergySession> findById(UUID id);

	Optional<EnergySession> findByKey(SessionSource source, String scope, String externalSessionId);

	List<EnergySession> findByKeys(SessionSource source, String scope, Collection<String> externalSessionIds);

	/** Ordenadas por inicio descendente. */
	PageResult<EnergySession> search(SessionCriteria criteria, PageQuery pageQuery);

	/** Ordenadas por inicio; para reportes. */
	List<EnergySession> findAll(SessionCriteria criteria);

	long count(SessionCriteria criteria);

	EnergyTotals totals(SessionCriteria criteria);

	/** Solo los dias con sesiones, en orden. */
	List<DailyEnergy> daily(SessionCriteria criteria);

	/** Distribucion por red del equipo; solo las redes con sesiones. */
	Map<ChargingNetwork, EnergyTotals> byNetwork(SessionCriteria criteria);

	/** Distribucion por plataforma de origen; solo las que tienen sesiones. */
	Map<SessionSource, EnergyTotals> bySource(SessionCriteria criteria);

	/** Distribucion por tipo de infraestructura; solo los tipos con sesiones. */
	Map<InfrastructureType, EnergyTotals> byType(SessionCriteria criteria);

	/** Equipos ordenados por energia descendente; {@code limit} <= 0 devuelve todos. */
	List<EquipmentEnergy> byEquipment(SessionCriteria criteria, int limit);

	/** Pendientes de vincular en orden de ID, a partir de {@code afterId} (exclusivo, null = desde el inicio). */
	List<EnergySession> findPendingLink(UUID afterId, int limit);
}
