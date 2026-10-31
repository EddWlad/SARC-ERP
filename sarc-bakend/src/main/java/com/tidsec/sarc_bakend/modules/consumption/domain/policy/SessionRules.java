package com.tidsec.sarc_bakend.modules.consumption.domain.policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

/**
 * Coherencia minima de una sesion. Una sesion de 0 kWh es valida (PRD RF-003.11); una que termina
 * antes de empezar o con energia no representable no se incorpora. Energia y termino solo se evaluan si
 * estan informados: que falten no es incoherente.
 */
public final class SessionRules {

	/** Umbral de revision humana, no limite fisico. */
	public static final BigDecimal MAX_ENERGY_KWH = new BigDecimal("1000");
	private static final BigDecimal STORAGE_LIMIT = new BigDecimal("1000000000");
	public static final String ABOVE_THRESHOLD = "ENERGY_ABOVE_THRESHOLD";

	private SessionRules() {
	}

	public static List<RowIssue> check(SessionReading reading) {
		List<RowIssue> issues = new ArrayList<>();
		if (reading.endedAt() != null && reading.endedAt().isBefore(reading.startedAt())) {
			issues.add(new RowIssue("END_BEFORE_START", "El termino es anterior al inicio"));
		}
		if (reading.energyKwh() != null && reading.energyKwh().signum() < 0) {
			issues.add(new RowIssue("NEGATIVE_ENERGY", "La energia no puede ser negativa"));
		}
		if (reading.energyKwh() != null && reading.energyKwh().compareTo(STORAGE_LIMIT) >= 0) {
			issues.add(new RowIssue("ENERGY_NOT_STORABLE", "La energía excede el valor admitido"));
		}
		else if (requiresReview(reading.energyKwh())) {
			issues.add(new RowIssue(ABOVE_THRESHOLD,
					"La energía supera 1.000 kWh; su cómputo depende de la revisión energética y de los demás controles"));
		}
		return issues;
	}

	public static void requireValid(SessionReading reading) {
		if (reading.startedAt() == null) {
			throw new BusinessRuleException("INVALID_SESSION", "La sesion necesita fecha de inicio");
		}
		List<RowIssue> issues = check(reading).stream().filter(SessionRules::isBlocking).toList();
		if (!issues.isEmpty()) {
			throw new BusinessRuleException("INVALID_SESSION", issues.getFirst().message());
		}
	}

	public static boolean requiresReview(BigDecimal energy) {
		return energy != null && energy.compareTo(MAX_ENERGY_KWH) > 0;
	}

	public static boolean isBlocking(RowIssue issue) {
		return !ABOVE_THRESHOLD.equals(issue.code());
	}
}
