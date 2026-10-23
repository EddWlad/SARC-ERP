package com.tidsec.sarc_bakend.modules.consumption.domain.policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowIssue;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Coherencia minima de una sesion. Una sesion de 0 kWh es valida (PRD RF-003.11); una que termina
 * antes de empezar o con energia imposible no se incorpora.
 */
public final class SessionRules {

	/** Ninguna sesion de carga de un vehiculo llega a este valor; mayor es un error del dato. */
	public static final BigDecimal MAX_ENERGY_KWH = new BigDecimal("1000");

	private SessionRules() {
	}

	public static List<RowIssue> check(SessionReading reading) {
		List<RowIssue> issues = new ArrayList<>();
		if (reading.endedAt().isBefore(reading.startedAt())) {
			issues.add(new RowIssue("END_BEFORE_START", "El termino es anterior al inicio"));
		}
		if (reading.energyKwh().signum() < 0) {
			issues.add(new RowIssue("NEGATIVE_ENERGY", "La energia no puede ser negativa"));
		}
		if (reading.energyKwh().compareTo(MAX_ENERGY_KWH) > 0) {
			issues.add(new RowIssue("ENERGY_OUT_OF_RANGE",
					"La energia supera " + MAX_ENERGY_KWH.toPlainString() + " kWh por sesion"));
		}
		return issues;
	}

	public static void requireValid(SessionReading reading) {
		if (reading.startedAt() == null || reading.endedAt() == null || reading.energyKwh() == null) {
			throw new BusinessRuleException("INVALID_SESSION", "La sesion necesita inicio, termino y energia");
		}
		List<RowIssue> issues = check(reading);
		if (!issues.isEmpty()) {
			throw new BusinessRuleException("INVALID_SESSION", issues.getFirst().message());
		}
	}
}
