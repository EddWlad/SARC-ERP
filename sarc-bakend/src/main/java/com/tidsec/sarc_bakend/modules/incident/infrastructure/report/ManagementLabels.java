package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.TimeMeasure;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Measure;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;

/** Textos del reporte de gestion que comparten el PDF y el Excel, para que ambos escriban lo mismo del mismo modo. */
final class ManagementLabels {

	static final String TITLE = "Gestión de incidencias";
	static final String LEGEND = "Referencia operativa, no SLA contractual";
	static final String NO_DATA = "Sin datos en el período";
	static final List<String> EXTERNAL = List.of(
			"Uptime: disponibilidad oficial validada por cargador. Consulte el módulo Uptime (/uptime).",
			"Intermitencias: tiempo fuera de línea y disponibilidad estimada de prueba. Consulte el módulo Intermitencias"
					+ " (/intermitencias).");

	private static final Map<String, String> PAIRS = Map.of("T1-T0", "Primera validación (T1−T0)",
			"T2-T0", "Primera acción (T2−T0)", "T3-T0", "Recuperación (T3−T0)", "T4-T0", "Cierre (T4−T0)", "T4-T3",
			"Recuperación a cierre (T4−T3)");

	private ManagementLabels() {
	}

	static String pair(String pair) {
		return PAIRS.getOrDefault(pair, pair);
	}

	static String measure(Measure measure) {
		return switch (measure) {
			case VALIDATION -> "Validación";
			case RECOVERY -> "Recuperación";
			case ESCALATION -> "Escalamiento";
		};
	}

	static String type(IncidentType type) {
		return IncidentLabels.type(type);
	}

	/** El rotulo de una clave de reparto: tipo, prioridad, clase y origen salen de su enumeracion; el resto, tal cual. */
	static String key(String dimension, String key) {
		try {
			return switch (dimension) {
				case "type" -> IncidentLabels.type(IncidentType.valueOf(key));
				case "priority" -> IncidentLabels.priority(Priority.valueOf(key));
				case "class" -> IncidentLabels.elementClass(ElementClass.valueOf(key));
				case "origin" -> IncidentLabels.origin(ReportOrigin.valueOf(key));
				default -> key;
			};
		}
		catch (IllegalArgumentException ex) {
			return key;
		}
	}

	/** Un promedio con un decimal, o «—» si no hay episodios que medir: nunca cero (FR-042). */
	static String average(BigDecimal value) {
		return value == null ? "—" : value.toPlainString().replace('.', ',');
	}

	static String excluded(TimeMeasure measure) {
		return "No aplica: " + measure.notApplicable() + " · Sin dato: " + measure.noData() + " · Abiertos: "
				+ measure.open();
	}

	static String percent(BigDecimal value) {
		return value == null ? "—" : value.toPlainString().replace('.', ',') + " %";
	}

	static String instant(CorporateBranding branding, Instant instant) {
		return instant == null ? "" : branding.dateTimeFormatter().format(instant);
	}
}
