package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Traduce los filtros del inventario a predicados; los comparten listado, conteos y reportes. */
final class EquipmentPredicates {

	private static final char LIKE_ESCAPE = '\\';

	private EquipmentPredicates() {
	}

	static Predicate matching(EquipmentSearchCriteria criteria, CriteriaBuilder cb, Path<Equipment> equipment) {
		List<Predicate> predicates = new ArrayList<>();
		if (criteria.text() != null && !criteria.text().isBlank()) {
			String pattern = "%" + escapeLike(criteria.text().strip().toLowerCase(Locale.ROOT)) + "%";
			predicates.add(cb.or(
					cb.like(cb.lower(equipment.get("name")), pattern, LIKE_ESCAPE),
					cb.like(cb.lower(equipment.get("assignedCode")), pattern, LIKE_ESCAPE),
					cb.like(cb.lower(equipment.get("serial")), pattern, LIKE_ESCAPE)));
		}
		if (criteria.network() != null) {
			predicates.add(cb.equal(equipment.get("network"), criteria.network()));
		}
		if (criteria.type() != null) {
			predicates.add(cb.equal(equipment.get("type"), criteria.type()));
		}
		if (criteria.brand() != null) {
			predicates.add(cb.equal(equipment.get("brand"), criteria.brand()));
		}
		predicates.add(criteria.adminStatus() == null
				? cb.notEqual(equipment.get("adminStatus"), AdministrativeStatus.DELETED)
				: cb.equal(equipment.get("adminStatus"), criteria.adminStatus()));
		if (criteria.usageStatus() != null) {
			predicates.add(cb.equal(equipment.get("usageStatus"), criteria.usageStatus()));
		}
		return cb.and(predicates.toArray(Predicate[]::new));
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
