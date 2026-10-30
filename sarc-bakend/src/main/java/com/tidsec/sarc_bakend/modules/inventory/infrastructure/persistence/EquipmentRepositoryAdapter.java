package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Connector;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentCount;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class EquipmentRepositoryAdapter implements EquipmentRepository {

	private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("id"));

	private final EquipmentJpaRepository jpa;

	@PersistenceContext
	private EntityManager entityManager;

	EquipmentRepositoryAdapter(EquipmentJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Equipment save(Equipment equipment) {
		return jpa.saveAndFlush(equipment);
	}

	@Override
	public Optional<Equipment> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<Equipment> findAllById(Collection<UUID> ids) {
		return jpa.findAllById(ids);
	}

	@Override
	public List<Equipment> findBySerials(Collection<String> serials) {
		return jpa.findBySerialIn(serials);
	}

	/**
	 * Sin vaciado previo (COMMIT): al editar, el equipo ya tiene los identificadores nuevos en memoria y
	 * el vaciado automatico los escribiria antes de la consulta, chocando con la restriccion UNIQUE en
	 * vez de devolver el conflicto legible. El propio equipo lo descarta quien llama.
	 */
	@Override
	public List<Equipment> findHoldersOf(String serial, String assignedCode, String uidPuk) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Equipment> query = cb.createQuery(Equipment.class);
		Root<Equipment> root = query.from(Equipment.class);
		List<Predicate> matches = new ArrayList<>();
		matches.add(cb.equal(root.get("serial"), serial));
		if (assignedCode != null) {
			matches.add(cb.equal(root.get("assignedCode"), assignedCode));
		}
		if (uidPuk != null) {
			matches.add(cb.equal(root.get("uidPuk"), uidPuk));
		}
		query.where(cb.or(matches.toArray(Predicate[]::new)));
		return entityManager.createQuery(query).setFlushMode(FlushModeType.COMMIT).getResultList();
	}

	@Override
	public PageResult<Equipment> search(EquipmentSearchCriteria criteria, PageQuery pageQuery) {
		Page<Equipment> page = jpa.findAll(matching(criteria),
				PageRequest.of(pageQuery.page(), pageQuery.size(), BY_NAME));
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Override
	public List<Equipment> findAll(EquipmentSearchCriteria criteria) {
		return jpa.findAll(matching(criteria), BY_NAME);
	}

	@Override
	public long count(EquipmentSearchCriteria criteria) {
		return jpa.count(matching(criteria));
	}

	@Override
	public List<EquipmentCount> countByDimensions(EquipmentSearchCriteria criteria) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Equipment> equipment = query.from(Equipment.class);
		List<Path<?>> dimensions = List.of(equipment.get("network"), equipment.get("type"), equipment.get("brand"),
				equipment.get("adminStatus"), equipment.get("usageStatus"));
		query.multiselect(dimensions.get(0), dimensions.get(1), dimensions.get(2), dimensions.get(3),
				dimensions.get(4), cb.count(equipment));
		query.where(EquipmentPredicates.matching(criteria, cb, equipment));
		query.groupBy(new ArrayList<>(dimensions));
		return entityManager.createQuery(query).getResultList().stream()
				.map(row -> new EquipmentCount(row.get(0, ChargingNetwork.class), row.get(1, InfrastructureType.class),
						row.get(2, EquipmentBrand.class), row.get(3, AdministrativeStatus.class),
						row.get(4, UsageStatus.class), row.get(5, Long.class)))
				.toList();
	}

	@Override
	public Map<String, Long> countActiveConnectorsByType(EquipmentSearchCriteria criteria) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Connector> connector = query.from(Connector.class);
		Join<Connector, Equipment> equipment = connector.join("equipment");
		Join<Connector, ConnectorType> type = connector.join("connectorType");
		query.multiselect(type.get("code"), cb.count(connector));
		query.where(cb.isTrue(connector.get("active")), EquipmentPredicates.matching(criteria, cb, equipment));
		query.groupBy(type.get("code"));
		query.orderBy(cb.asc(type.get("code")));
		Map<String, Long> counts = new LinkedHashMap<>();
		entityManager.createQuery(query).getResultList()
				.forEach(row -> counts.put(row.get(0, String.class), row.get(1, Long.class)));
		return counts;
	}

	private static Specification<Equipment> matching(EquipmentSearchCriteria criteria) {
		return (root, query, cb) -> EquipmentPredicates.matching(criteria, cb, root);
	}
}
