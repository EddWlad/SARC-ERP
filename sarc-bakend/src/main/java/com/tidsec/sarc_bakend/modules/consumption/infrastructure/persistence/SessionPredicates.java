package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Condiciones del filtro de sesiones, comunes al listado y a todos los agregados. */
final class SessionPredicates {

	private static final char LIKE_ESCAPE = '\\';

	private SessionPredicates() {
	}

	static Predicate[] of(SessionCriteria criteria, Root<EnergySession> root, CriteriaBuilder cb) {
		List<Predicate> predicates = new ArrayList<>();
		if (criteria.from() != null) {
			predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), criteria.from()));
		}
		if (criteria.to() != null) {
			predicates.add(cb.lessThan(root.get("startDate"), criteria.to()));
		}
		if (criteria.network() != null) {
			predicates.add(cb.equal(root.get("network"), criteria.network()));
		}
		if (criteria.type() != null) {
			predicates.add(cb.equal(root.get("infrastructureType"), criteria.type()));
		}
		if (criteria.source() != null) {
			predicates.add(cb.equal(root.get("source"), criteria.source()));
		}
		if (criteria.equipmentId() != null) {
			predicates.add(cb.equal(root.get("equipmentId"), criteria.equipmentId()));
		}
		if (criteria.state() != null) {
			predicates.add(cb.equal(root.get("state"), criteria.state()));
		}
		if (criteria.text() != null && !criteria.text().isBlank()) {
			String pattern = "%" + escape(criteria.text().strip().toLowerCase(Locale.ROOT)) + "%";
			predicates.add(cb.or(
					cb.like(cb.lower(root.get("externalSessionId")), pattern, LIKE_ESCAPE),
					cb.like(cb.lower(root.get("sourceCharger")), pattern, LIKE_ESCAPE),
					cb.like(cb.lower(root.get("sourceSite")), pattern, LIKE_ESCAPE),
					cb.like(cb.lower(root.get("sourceSerial")), pattern, LIKE_ESCAPE)));
		}
		return predicates.toArray(Predicate[]::new);
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
