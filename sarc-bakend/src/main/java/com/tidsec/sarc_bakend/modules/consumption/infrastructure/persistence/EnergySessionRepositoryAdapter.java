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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EquipmentEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionAggregate;
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
	public void flushEnergyDecision(EnergySession session) {
		entityManager.flush();
		int changed = entityManager.createQuery("update EnergySession e set e.version = e.version + 1 where e.id = :id and e.version = :version")
				.setParameter("id", session.getId()).setParameter("version", session.getVersion()).executeUpdate();
		if (changed != 1) {
			throw new com.tidsec.sarc_bakend.shared.error.ConflictException("CONCURRENT_MODIFICATION",
					"La sesión cambió; recargue antes de decidir");
		}
		entityManager.refresh(session);
	}

	@Override
	public void saveRevision(SessionRevision revision) {
		revisions.save(revision);
	}

	@Override
	public List<SessionRevision> history(UUID sessionId) {
		return revisions.findBySessionIdOrderByRevisionDesc(sessionId);
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
		PageRequest request = PageRequest.of(pageQuery.page(), pageQuery.size(), NEWEST_FIRST);
		Slice<EnergySession> slice = jpa.findBy(
				(Specification<EnergySession>) (root, query, cb) -> cb.and(SessionPredicates.of(criteria, root, cb)),
				query -> query.slice(request));
		Page<EnergySession> page = new PageImpl<>(slice.getContent(), request, count(criteria));
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
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<EnergySession> root = query.from(EnergySession.class);
		// Contar una constante, no el ID, permite resolverlo solo con el indice del periodo.
		query.select(cb.count(cb.literal(1))).where(SessionPredicates.of(criteria, root, cb));
		return entityManager.createQuery(query).getSingleResult();
	}

	@Override
	public List<SessionAggregate> aggregate(SessionCriteria criteria) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		List<Expression<?>> groups = List.of(root.get("startDate"), root.get("network"), root.get("source"),
				root.get("infrastructureType"), root.get("equipmentId"), root.get("energyReview"));
		query.multiselect(groups.get(0), groups.get(1), groups.get(2), groups.get(3), groups.get(4), groups.get(5),
				cb.count(root), energySum(root, cb), readings(root, cb))
				.where(SessionPredicates.of(criteria, root, cb))
				.groupBy(groups);
		return entityManager.createQuery(query).getResultList().stream()
				.map(row -> new SessionAggregate(row.get(0, LocalDate.class), row.get(1, ChargingNetwork.class),
						row.get(2, SessionSource.class), row.get(3, InfrastructureType.class), row.get(4, UUID.class),
						row.get(5, EnergyReviewStatus.class), row.get(6, Long.class), row.get(7, BigDecimal.class),
						row.get(8, Long.class)))
				.toList();
	}

	@Override
	public Map<SessionState, EnergyTotals> totalsByState(SessionCriteria criteria, Collection<SessionState> states) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		Expression<SessionState> state = root.get("state");
		query.multiselect(state, cb.count(root), energySum(root, cb), readings(root, cb),
				cb.countDistinct(root.get("equipmentId")))
				.where(cb.and(SessionPredicates.of(criteria, root, cb)), state.in(states))
				.groupBy(state);
		Map<SessionState, EnergyTotals> totals = new EnumMap<>(SessionState.class);
		for (Tuple row : entityManager.createQuery(query).getResultList()) {
			totals.put(row.get(0, SessionState.class), EnergyTotals.of(row.get(1, Long.class),
					row.get(2, BigDecimal.class), row.get(3, Long.class), row.get(4, Long.class)));
		}
		return totals;
	}

	@Override
	public List<EquipmentEnergy> byEquipment(SessionCriteria criteria) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<EnergySession> root = query.from(EnergySession.class);
		Expression<UUID> equipment = root.get("equipmentId");
		Expression<BigDecimal> energy = energySum(root, cb);
		query.multiselect(equipment, cb.count(root), energy)
				.where(cb.and(SessionPredicates.of(criteria, root, cb)), cb.isNotNull(equipment))
				.groupBy(equipment)
				.having(cb.greaterThan(readings(root, cb), 0L))
				.orderBy(cb.desc(energy), cb.asc(equipment));
		var typed = entityManager.createQuery(query);
		return typed.getResultList().stream()
				.map(row -> new EquipmentEnergy(row.get(0, UUID.class), row.get(1, Long.class),
						row.get(2, BigDecimal.class)))
				.toList();
	}

	@Override
	public List<EnergySession> findPendingLink(UUID afterId, int limit) {
		return jpa.findPendingLink(afterId, PageRequest.of(0, limit));
	}

	/** Suma de la energia conocida; nula si ninguna sesion informa energia, nunca cero por ausencia. */
	private static Expression<BigDecimal> energySum(Root<EnergySession> root, CriteriaBuilder cb) {
		return cb.sum(root.<BigDecimal>get("energyKwh"));
	}

	/** Sesiones con lectura de energia, incluidas las de 0 kWh. */
	private static Expression<Long> readings(Root<EnergySession> root, CriteriaBuilder cb) {
		return cb.count(root.get("energyKwh"));
	}
}
