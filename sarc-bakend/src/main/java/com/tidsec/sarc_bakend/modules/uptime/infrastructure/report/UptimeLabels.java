package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.UnitState;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

/** Textos comunes de PDF y Excel. La falta de cifra se dice con palabras, nunca como 0. */
final class UptimeLabels {

	static final String NO_DATA = "Sin datos";
	static final String NO_OFFICIAL = "No hay cifra semanal oficial para este alcance.";
	static final String FORMULA = "Uptime = (tiempo evaluado - fuera de línea) / tiempo evaluado. El tiempo con falla se "
			+ "informa aparte y no se descuenta.";
	static final String EQUIPMENT_NOTE = "El promedio por equipo pondera sus conectores por minutos evaluados; no es una "
			+ "medición del equipo completo.";

	/** Orden de presentacion de la cobertura. */
	static final List<UnitState> STATES = List.of(UnitState.VALIDATED, UnitState.PENDING_REVIEW, UnitState.NO_DATA,
			UnitState.WITHOUT_MEASUREMENT, UnitState.OUT_OF_SCOPE, UnitState.RETIRED, UnitState.EXCLUDED);

	private static final Map<UnitState, String> STATE_LABELS = Map.of(UnitState.VALIDATED, "Validadas",
			UnitState.PENDING_REVIEW, "Pendientes de revisión", UnitState.NO_DATA, NO_DATA, UnitState.WITHOUT_MEASUREMENT,
			"Sin medición", UnitState.OUT_OF_SCOPE, "Fuera de alcance", UnitState.RETIRED, "Retirado", UnitState.EXCLUDED,
			"Excluidas");
	private static final Map<PeriodType, String> PERIOD_LABELS = Map.of(PeriodType.ROLLING_24H, "Últimas 24 horas",
			PeriodType.ROLLING_7D, "Últimos 7 días", PeriodType.ROLLING_30D, "Últimos 30 días", PeriodType.EXACT_WEEK,
			"Semana exacta");

	private UptimeLabels() {
	}

	static String of(UnitState state) {
		return STATE_LABELS.get(state);
	}

	static String of(PeriodType type) {
		return PERIOD_LABELS.get(type);
	}

	static String percent(BigDecimal value) {
		return value == null ? NO_DATA : decimal(value) + " %";
	}

	static String decimal(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
	}

	static String hours(long minutes) {
		return decimal(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
	}

	static String code(MeasurableConnector connector) {
		return connector.assignedCode() == null ? "" : connector.assignedCode();
	}

	static String connector(MeasurableConnector connector) {
		return "Conector " + connector.ordinal() + " · " + connector.connectorTypeName();
	}
}
