package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
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

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.Connector;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentCount;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
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
	public List<Equipment> findHoldersOf(String serial, String assignedCode, String uidPuk) {
		Specification<Equipment> holders = (root, query, cb) -> {
			List<Predicate> matches = new ArrayList<>();
			matches.add(cb.equal(root.get("serial"), serial));
			matches.add(cb.equal(root.get("assignedCode"), assignedCode));
			if (uidPuk != null) {
				matches.add(cb.equal(root.get("uidPuk"), uidPuk));
			}
			return cb.or(matches.toArray(Predicate[]::new));
		};
		return jpa.findAll(holders);
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
