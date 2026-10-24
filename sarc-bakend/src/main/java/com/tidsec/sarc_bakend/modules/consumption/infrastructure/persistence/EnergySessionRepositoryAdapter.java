package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EquipmentEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

@Component
class EnergySessionRepositoryAdapter implements EnergySessionRepository {

	/** Limite de parametros por consulta IN, por debajo del maximo de PostgreSQL. */
	private static final int IN_CHUNK = 1_000;
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by("id"));

	private final EnergySessionJpaRepository jpa;
	private final SessionRevisionJpaRepository revisions;

	@PersistenceContext
	private EntityManager entityManager;

	EnergySessionRepositoryAdapter(EnergySessionJpaRepository jpa, SessionRevisionJpaRepository revisions) {
		this.jpa = jpa;
		this.revisions = revisions;
	}

	@Override
	public EnergySession save(EnergySession session) {
		return jpa.save(session);
	}

	@Override
	public void saveRevision(SessionRevision revision) {
		revisions.save(revision);
	}

	@Override
	public Optional<EnergySession> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public Optional<EnergySession> findByKey(SessionSource source, String scope, String externalSessionId) {
		return jpa.findBySourceAndSourceScopeAndExternalSessionId(source, scope, externalSessionId);
	}

	@Override
	public List<EnergySession> findByKeys(SessionSource source, String scope, Collection<String> externalSessionIds) {
		List<String> ids = List.copyOf(externalSessionIds);
		List<EnergySession> found = new ArrayList<>();
		for (int start = 0; start < ids.size(); start += IN_CHUNK) {
			found.addAll(jpa.findBySourceAndSourceScopeAndExternalSessionIdIn(source, scope,
					ids.subList(start, Math.min(start + IN_CHUNK, ids.size()))));
		}
		return found;
	}

	@Override
	public PageResult<EnergySession> search(SessionCriteria criteria, PageQuery pageQuery) {
		Page<EnergySession> page = jpa.findAll(
				(root, query, cb) -> cb.and(SessionPredicates.of(criteria, root, cb)),
				PageRequest.of(pageQuery.page(), pageQuery.size(), NEWEST_FIRST));
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Override
	public List<EnergySession> findAll(SessionCriteria criteria) {
		return jpa.findAll((root, query, cb) -> cb.and(SessionPredicates.of(criteria, root, cb)),
				Sort.by("startedAt").and(Sort.by("id")));
	}

	@Override
	public long count(SessionCriteria criteria) {
		return jpa.count((root, query, cb) -> cb.and(SessionPredicates.of(criteria, root, cb)));
	}

	@Override
	public EnergyTotals totals(SessionCriteria criteria) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		query.multiselect(cb.count(root), energySum(root, cb), cb.countDistinct(root.get("equipmentId")))
				.where(SessionPredicates.of(criteria, root, cb));
		Tuple row = entityManager.createQuery(query).getSingleResult();
		return new EnergyTotals(row.get(0, Long.class), row.get(1, BigDecimal.class), row.get(2, Long.class));
	}

	@Override
	public List<DailyEnergy> daily(SessionCriteria criteria) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		Expression<LocalDate> date = root.get("startDate");
		query.multiselect(date, cb.count(root), energySum(root, cb))
				.where(SessionPredicates.of(criteria, root, cb))
				.groupBy(date)
				.orderBy(cb.asc(date));
		return entityManager.createQuery(query).getResultList().stream()
				.map(row -> new DailyEnergy(row.get(0, LocalDate.class), row.get(1, Long.class),
						row.get(2, BigDecimal.class)))
				.toList();
	}

	@Override
	public Map<ChargingNetwork, EnergyTotals> byNetwork(SessionCriteria criteria) {
		return groupBy(criteria, "network", ChargingNetwork.class);
	}

	@Override
	public Map<SessionSource, EnergyTotals> bySource(SessionCriteria criteria) {
		return groupBy(criteria, "source", SessionSource.class);
	}

	@Override
	public Map<InfrastructureType, EnergyTotals> byType(SessionCriteria criteria) {
		return groupBy(criteria, "infrastructureType", InfrastructureType.class);
	}

	/** Sesiones, energia y equipos distintos agrupados por un atributo enumerado de la sesion. */
	private <K extends Enum<K>> Map<K, EnergyTotals> groupBy(SessionCriteria criteria, String attribute,
			Class<K> keyType) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		Expression<K> key = root.get(attribute);
		query.multiselect(key, cb.count(root), energySum(root, cb), cb.countDistinct(root.get("equipmentId")))
				.where(cb.and(SessionPredicates.of(criteria, root, cb)), cb.isNotNull(key))
				.groupBy(key);
		Map<K, EnergyTotals> totals = new EnumMap<>(keyType);
		for (Tuple row : entityManager.createQuery(query).getResultList()) {
			totals.put(row.get(0, keyType), new EnergyTotals(row.get(1, Long.class), row.get(2, BigDecimal.class),
					row.get(3, Long.class)));
		}
		return totals;
	}

	@Override
	public List<EquipmentEnergy> byEquipment(SessionCriteria criteria, int limit) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		Expression<UUID> equipment = root.get("equipmentId");
		Expression<BigDecimal> energy = energySum(root, cb);
		query.multiselect(equipment, cb.count(root), energy)
				.where(cb.and(SessionPredicates.of(criteria, root, cb)), cb.isNotNull(equipment))
				.groupBy(equipment)
				.orderBy(cb.desc(energy), cb.asc(equipment));
		var typed = entityManager.createQuery(query);
		if (limit > 0) {
			typed.setMaxResults(limit);
		}
		return typed.getResultList().stream()
				.map(row -> new EquipmentEnergy(row.get(0, UUID.class), row.get(1, Long.class),
						row.get(2, BigDecimal.class)))
				.toList();
	}

	@Override
	public List<EnergySession> findPendingLink(UUID afterId, int limit) {
		return jpa.findPendingLink(afterId, PageRequest.of(0, limit));
	}

	/** Suma de energia que vale 0 (no nulo) cuando no hay sesiones. */
	private static Expression<BigDecimal> energySum(Root<EnergySession> root, CriteriaBuilder cb) {
		return cb.coalesce(cb.sum(root.<BigDecimal>get("energyKwh")), BigDecimal.ZERO);
	}
}
