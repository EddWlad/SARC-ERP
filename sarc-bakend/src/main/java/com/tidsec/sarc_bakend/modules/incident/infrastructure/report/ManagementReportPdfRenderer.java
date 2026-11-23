package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.application.port.ManagementReportRenderer;
import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.Kpis;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.ReferenceCount;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.Kpi;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * PDF corporativo A4 horizontal de «Gestión de incidencias» (contracts/reports.md, seccion 2): resumen ejecutivo,
 * indicadores de tiempo, referencias operativas, graficos sin ponderar con su {@code n}, repartos, visitas, detalle por
 * ticket y episodio, fuentes externas y metodologia. Un grafico sin datos se omite (FR-081); las referencias llevan la
 * leyenda «Referencia operativa, no SLA contractual» (FR-051) y no hay cifras de disponibilidad (P-13).
 */
@Component
class ManagementReportPdfRenderer implements ManagementReportRenderer {

	private final CorporateBranding branding;

	ManagementReportPdfRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] render(ManagementData data) {
		Kpis kpis = data.kpis();
		CorporatePdf pdf = CorporatePdf.create(branding, ManagementLabels.TITLE, scope(data), data.cutOff());

		section(pdf, "1. Resumen ejecutivo");
		var summary = kpis.summary();
		pdf.kpis(List.of(new Kpi("Tickets únicos", String.valueOf(summary.uniqueTickets())),
				new Kpi("Episodios", String.valueOf(summary.episodes())),
				new Kpi("Abiertos al corte", String.valueOf(data.openTicketsAtCutOff())),
				new Kpi("Episodios cerrados", String.valueOf(summary.closedEpisodes())),
				new Kpi("Reabiertos", String.valueOf(summary.reopened())),
				new Kpi("Críticos", String.valueOf(summary.critical()))));
		pdf.note("Cerrados por duplicado o error (aparte, fuera de los promedios): " + summary.excluded());
		if (summary.episodes() == 0) {
			pdf.note(ManagementLabels.NO_DATA);
		}

		section(pdf, "2. Indicadores de tiempo");
		pdf.table(List.of("Medida", "Promedio (min)", "n", "No aplica", "Sin dato", "Abiertos o pendientes"),
				kpis.measures().stream().map(measure -> List.of(ManagementLabels.pair(measure.pair()),
						ManagementLabels.average(measure.averageMinutes()), String.valueOf(measure.n()),
						String.valueOf(measure.notApplicable()), String.valueOf(measure.noData()),
						String.valueOf(measure.open()))).toList(),
				new float[] { 4, 2, 1, 1.5f, 1.5f, 2.5f });
		pdf.note("Validaciones dentro de la referencia: " + ManagementLabels.percent(kpis.validationPercent()) + " (n = "
				+ kpis.validationN() + ")");

		section(pdf, "3. Referencias operativas");
		pdf.note(ManagementLabels.LEGEND + ". Versión de referencias: "
				+ (data.referenceVersions().isBlank() ? "—" : data.referenceVersions()) + ".");
		if (kpis.references().isEmpty()) {
			pdf.note("Ningún episodio con referencia evaluable en el período.");
		}
		else {
			pdf.table(List.of("Medida", "Tipo", "Dentro", "Fuera", "No evaluables"),
					kpis.references().stream().map(this::reference).toList(), new float[] { 3, 5, 1.5f, 1.5f, 2 });
		}
		if (!kpis.withoutReference().isEmpty()) {
			pdf.note("Sin referencia aprobada (se muestra el tiempo sin juicio): " + kpis.withoutReference().stream()
					.map(ManagementLabels::type).reduce((a, b) -> a + ", " + b).orElse(""));
		}
		if (!kpis.deviations().isEmpty()) {
			pdf.table(List.of("Ticket", "Episodio", "Tipo", "Medida", "Justificación"),
					kpis.deviations().stream().map(deviation -> List.of(deviation.code(),
							String.valueOf(deviation.ordinal()), ManagementLabels.type(deviation.type()),
							ManagementLabels.measure(deviation.measure()),
							deviation.justification() == null ? "Sin justificación" : deviation.justification())).toList(),
					new float[] { 2, 1, 4, 2, 6 });
		}

		section(pdf, "4. Gráficos (sin ponderar)");
		bars(pdf, "Episodios por fecha de T0", data.kpis().byDay(), Function.identity());
		bars(pdf, "Episodios por red", kpis.byNetwork(), Function.identity());
		bars(pdf, "Episodios por tipo", kpis.byType(), key -> ManagementLabels.key("type", key));
		bars(pdf, "Episodios por prioridad", kpis.byPriority(), key -> ManagementLabels.key("priority", key));
		var visits = kpis.visits();
		bars(pdf, "Visitas por etapa alcanzada", visitMap(visits.requested(), visits.confirmed(),
				visits.cancelledOrPostponed(), visits.executed()), Function.identity());
		bars(pdf, "Reaperturas", summary.reopened() == 0 ? Map.of() : Map.of("Episodios reabiertos", summary.reopened()),
				Function.identity());
		bars(pdf, "Tiempos excesivos (fuera de referencia) por tipo", outside(kpis), Function.identity());
		bars(pdf, "Equipos con más episodios", repeaters(kpis), Function.identity());

		section(pdf, "5. Repartos");
		distribution(pdf, "Por tipo", kpis.byType(), "type");
		distribution(pdf, "Por red (conservada en T0)", kpis.byNetwork(), "network");
		distribution(pdf, "Por clase de elemento", kpis.byClass(), "class");
		distribution(pdf, "Por equipo", kpis.byEquipment(), "equipment");
		distribution(pdf, "Por prioridad", kpis.byPriority(), "priority");
		distribution(pdf, "Por origen del aviso", kpis.byOrigin(), "origin");

		section(pdf, "6. Visitas técnicas");
		pdf.table(List.of("Solicitadas", "Confirmadas", "Canceladas o aplazadas", "Ejecutadas"),
				List.of(List.of(String.valueOf(visits.requested()), String.valueOf(visits.confirmed()),
						String.valueOf(visits.cancelledOrPostponed()), String.valueOf(visits.executed()))),
				new float[] { 1, 1, 1, 1 });

		section(pdf, "7. Detalle por ticket y episodio");
		pdf.note("Marcas: R registrado · R* retroactivo · N/A no aplica · S/D sin dato · — pendiente. Los tiempos van en minutos;"
				+ " vacío significa que falta un hito, nunca cero.");
		if (data.episodes().isEmpty()) {
			pdf.note(ManagementLabels.NO_DATA);
		}
		else {
			pdf.table(List.of("Ticket", "Ep.", "Elemento", "Tipo", "Prioridad", "T0", "T1", "T2", "T3", "T4", "T1−T0",
					"T2−T0", "T3−T0", "T4−T0", "T4−T3", "Referencia"),
					data.episodes().stream().map(this::detail).toList(),
					new float[] { 1.6f, 0.7f, 3.2f, 2.2f, 1.4f, 1.4f, 1.4f, 1.4f, 1.4f, 1.4f, 1, 1, 1, 1, 1, 2.2f });
		}

		section(pdf, "8. Fuentes externas");
		ManagementLabels.EXTERNAL.forEach(pdf::note);
		pdf.note("Este informe no muestra cifras de disponibilidad: no se recalculan aquí ni se presentan estimaciones como"
				+ " oficiales.");

		section(pdf, "9. Metodología");
		for (String note : methodology()) {
			pdf.note(note);
		}
		return pdf.finish();
	}

	/** El titulo de una seccion nunca queda solo al pie de una pagina: se reserva espacio para su primer bloque. */
	private static void section(CorporatePdf pdf, String title) {
		pdf.ensureSpace(130);
		pdf.section(title);
	}

	private List<String> reference(ReferenceCount count) {
		return List.of(ManagementLabels.measure(count.measure()), ManagementLabels.type(count.type()),
				String.valueOf(count.within()), String.valueOf(count.outside()), String.valueOf(count.notEvaluable()));
	}

	private List<String> detail(ManagementData.EpisodeLine line) {
		return List.of(line.code(), String.valueOf(line.ordinal()), line.element(), line.type(), line.priority(),
				mark(line.t0Mark(), line.t0()), mark(line.t1Mark(), line.t1()), mark(line.t2Mark(), line.t2()),
				mark(line.t3Mark(), line.t3()), mark(line.t4Mark(), line.t4()), minutes(line.t1Minutes()),
				minutes(line.t2Minutes()), minutes(line.t3Minutes()), minutes(line.t4Minutes()),
				minutes(line.t4MinusT3()), line.referenceResults() == null ? "" : line.referenceResults());
	}

	private String mark(String mark, String instant) {
		if (instant == null) {
			return mark;
		}
		return mark + " " + branding.dateTimeFormatter().format(java.time.Instant.parse(instant));
	}

	private static String minutes(Integer value) {
		return value == null ? "" : String.valueOf(value);
	}

	private String scope(ManagementData data) {
		List<String> parts = new ArrayList<>();
		parts.add("Período: " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(data.from()) + " al "
				+ java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(data.to()));
		if (data.network() != null) {
			parts.add("Red: " + data.network());
		}
		if (data.elementClass() != null) {
			parts.add("Clase: " + ManagementLabels.key("class", data.elementClass().name()));
		}
		if (data.incidentType() != null) {
			parts.add("Tipo: " + ManagementLabels.type(data.incidentType()));
		}
		if (data.priority() != null) {
			parts.add("Prioridad: " + ManagementLabels.key("priority", data.priority().name()));
		}
		return String.join(" · ", parts);
	}

	private static Map<String, Integer> visitMap(int requested, int confirmed, int cancelled, int executed) {
		Map<String, Integer> map = new java.util.LinkedHashMap<>();
		if (requested > 0) {
			map.put("Solicitadas", requested);
			map.put("Confirmadas", confirmed);
			map.put("Canceladas o aplazadas", cancelled);
			map.put("Ejecutadas", executed);
		}
		return map;
	}

	private static Map<String, Integer> outside(Kpis kpis) {
		Map<String, Integer> map = new java.util.TreeMap<>();
		kpis.references().stream().filter(count -> count.outside() > 0).forEach(count -> map
				.merge(ManagementLabels.type(count.type()), count.outside(), Integer::sum));
		return map;
	}

	private static Map<String, Integer> repeaters(Kpis kpis) {
		Map<String, Integer> map = new java.util.LinkedHashMap<>();
		kpis.repeaters().forEach(repeater -> map.put(repeater.label(), repeater.episodes()));
		return map;
	}

	/** Un grafico de barras sin ponderar, con su n; se omite si no hay datos (FR-081). */
	private void bars(CorporatePdf pdf, String title, Map<String, Integer> counts, Function<String, String> label) {
		if (counts.isEmpty()) {
			return;
		}
		int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
		int n = counts.values().stream().mapToInt(Integer::intValue).sum();
		pdf.ensureSpace(40 + counts.size() * 12);
		List<CorporatePdf.Bar> bars = counts.entrySet().stream().map(entry -> new CorporatePdf.Bar(
				label.apply(entry.getKey()), max == 0 ? null : (float) entry.getValue() / max,
				String.valueOf(entry.getValue()))).toList();
		pdf.bars(List.of(title + " (n = " + n + ")", "Cantidad"), bars);
	}

	private void distribution(CorporatePdf pdf, String title, Map<String, Integer> counts, String dimension) {
		if (counts.isEmpty()) {
			return;
		}
		pdf.ensureSpace(40 + counts.size() * 14);
		pdf.table(List.of(title, "Episodios"), counts.entrySet().stream()
				.map(entry -> List.of(ManagementLabels.key(dimension, entry.getKey()), String.valueOf(entry.getValue())))
				.toList(), new float[] { 6, 1 });
	}

	static List<String> methodology() {
		return List.of(
				"Alcance: episodios cuyo T0 cae en el período, con inicio inclusivo y fin exclusivo. «Abiertos al corte» cuenta"
						+ " tickets del período abiertos en la fecha de corte.",
				"Medidas: T1−T0 primera validación, T2−T0 primera acción, T3−T0 recuperación, T4−T0 cierre y T4−T3"
						+ " recuperación a cierre. Minutos enteros, redondeados hacia abajo; el promedio lleva un decimal.",
				"Denominador n: episodios con ambos hitos registrados. Se cuentan aparte los «No aplica», los «Sin dato" + "» y"
						+ " los episodios abiertos o pendientes; un hito sin hora nunca cuenta como cero.",
				"Los cerrados como duplicado o registrados por error se informan aparte y quedan fuera de los promedios,"
						+ " los repartos y las referencias.",
				"Referencias: valores del protocolo como referencia operativa, no SLA contractual, por versión vigente en el"
						+ " T0 de cada episodio. Los tipos sin referencia se muestran sin juicio.",
				"Gráficos y repartos sin ponderar; no hay pesos aprobados. Los equipos con más episodios no llevan etiqueta de"
						+ " recurrente.",
				"Protocolo: la portada del documento fuente indica 2.0 y sus páginas interiores 1.0 (P-02, pendiente de"
						+ " confirmar).");
	}
}
