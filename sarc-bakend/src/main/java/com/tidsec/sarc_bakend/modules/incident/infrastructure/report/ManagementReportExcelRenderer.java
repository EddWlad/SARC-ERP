package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.application.port.ManagementReportRenderer;
import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.Kpis;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporateWorkbook;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Excel de «Gestión de incidencias» (FR-085): una hoja por bloque, con encabezados y filtros, para que cada cifra de
 * {@code Resumen} se compruebe con un conteo o un promedio sobre {@code Episodios}. Los tiempos van como numeros y un hito
 * sin hora queda vacio, nunca en cero. Las hojas son {@code Resumen}, {@code Tickets}, {@code Episodios},
 * {@code Bitácora}, {@code Comunicaciones}, {@code Visitas} y {@code Metodología}.
 */
@Component
class ManagementReportExcelRenderer implements ManagementReportRenderer {

	private final CorporateBranding branding;

	ManagementReportExcelRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] render(ManagementData data) {
		String scope = "Período: " + data.from() + " al " + data.to() + " · Zona horaria: " + branding.zone().getId();
		return CorporateWorkbook.create(branding, scope, data.cutOff())
				.sheet("Resumen", ManagementLabels.TITLE, List.of("Indicador", "Valor", "Denominador", "Excluidos", "Nota"),
						summary(data))
				.sheet("Tickets", "Tickets del período",
						List.of("Código", "Estado actual", "Estado al final del período", "Clase", "Elemento", "Red en T0",
								"Tipo", "Prioridad", "Origen", "Responsable", "Episodios"),
						data.tickets().stream().map(line -> row(line.code(), line.currentState(), line.stateAtPeriodEnd(),
								line.elementClass(), line.element(), line.network(), line.incidentType(), line.priority(),
								line.origin(), line.responsible(), line.episodes())).toList())
				.sheet("Episodios", "Episodios con T0 en el período",
						List.of("Código", "Episodio", "Tipo", "Prioridad", "Elemento", "Red en T0", "Estado", "T0", "T0 marca",
								"T1", "T1 marca", "T2", "T2 marca", "T3", "T3 marca", "T4", "T4 marca", "T1−T0 (min)",
								"T2−T0 (min)", "T3−T0 (min)", "T4−T0 (min)", "T4−T3 (min)", "Versión de referencias",
								"Resultado de referencia", "Desviación", "Resultado de cierre", "Causa",
								"Transcurrido abierto (min)"),
						data.episodes().stream().map(this::episode).toList())
				.sheet("Bitácora", "Bitácora de eventos",
						List.of("Código", "Episodio", "Tipo", "Ocurrió", "Registrado", "Autor", "Retroactivo", "Anulado",
								"Texto"),
						data.events().stream().map(line -> row(line.code(), line.episode(), line.type(),
								ManagementLabels.instant(branding, line.occurredAt()),
								ManagementLabels.instant(branding, line.recordedAt()), line.author(),
								yesNo(line.retroactive()), yesNo(line.annulled()), line.text())).toList())
				.sheet("Comunicaciones", "Comunicaciones registradas",
						List.of("Código", "Canal", "Tipo", "Enviado", "Registrado", "Autor", "Retroactiva", "Anulada"),
						data.communications().stream().map(line -> row(line.code(), line.channel(), line.kind(),
								ManagementLabels.instant(branding, line.sentAt()),
								ManagementLabels.instant(branding, line.recordedAt()), line.author(),
								yesNo(line.retroactive()), yesNo(line.annulled()))).toList())
				.sheet("Visitas", "Etapas de visita",
						List.of("Código", "Proveedor", "Etapa", "Hora", "Nota"),
						data.visits().stream().map(line -> row(line.code(), line.provider(), line.stage(),
								ManagementLabels.instant(branding, line.at()), line.note())).toList())
				.sheet("Metodología", "Metodología y fuentes", List.of("Regla"), methodology())
				.finish();
	}

	private List<List<Object>> summary(ManagementData data) {
		Kpis kpis = data.kpis();
		var summary = kpis.summary();
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("Tickets únicos", summary.uniqueTickets(), null, null, "Tickets con algún episodio en el período"));
		rows.add(row("Episodios", summary.episodes(), null, null, "Filas de la hoja Episodios"));
		rows.add(row("Episodios abiertos", summary.openEpisodes(), null, null, "Sin T4 al corte, sin duplicados ni errores"));
		rows.add(row("Episodios cerrados", summary.closedEpisodes(), null, null,
				"Con T4 al corte, sin duplicados ni errores"));
		rows.add(row("Reabiertos", summary.reopened(), null, null, "Episodios con número mayor que 1"));
		rows.add(row("Críticos", summary.critical(), null, null, "Episodios de prioridad crítica"));
		rows.add(row("Cerrados por duplicado o error", summary.excluded(), null, null,
				"Aparte: quedan fuera de los promedios y los repartos"));
		rows.add(row("Tickets abiertos al corte", data.openTicketsAtCutOff(), null, null,
				"Tickets del período abiertos en la fecha de corte"));
		kpis.measures().forEach(measure -> rows.add(row("Promedio " + ManagementLabels.pair(measure.pair()) + " (min)",
				measure.averageMinutes() == null ? null : measure.averageMinutes().doubleValue(), measure.n(),
				"No aplica " + measure.notApplicable() + " · Sin dato " + measure.noData() + " · Abiertos " + measure.open(),
				"Minutos enteros hacia abajo; vacío si no hay episodios medibles")));
		rows.add(row("Validaciones dentro de la referencia (%)",
				kpis.validationPercent() == null ? null : kpis.validationPercent().doubleValue(), kpis.validationN(), null,
				ManagementLabels.LEGEND));
		rows.add(row("Visitas solicitadas", kpis.visits().requested(), null, null, "Visitas de episodios del período"));
		rows.add(row("Visitas confirmadas", kpis.visits().confirmed(), kpis.visits().requested(), null, "Etapa alcanzada"));
		rows.add(row("Visitas canceladas o aplazadas", kpis.visits().cancelledOrPostponed(), kpis.visits().requested(), null,
				"Etapa alcanzada"));
		rows.add(row("Visitas ejecutadas", kpis.visits().executed(), kpis.visits().requested(), null, "Etapa alcanzada"));
		return rows;
	}

	private List<Object> episode(ManagementData.EpisodeLine line) {
		return row(line.code(), line.ordinal(), line.type(), line.priority(), line.element(), line.network(),
				TicketState.valueOf(line.state()) == TicketState.OPEN ? IncidentLabels.OPEN : IncidentLabels.CLOSED,
				line.t0(), line.t0Mark(), line.t1(), line.t1Mark(), line.t2(), line.t2Mark(), line.t3(), line.t3Mark(),
				line.t4(), line.t4Mark(), line.t1Minutes(), line.t2Minutes(), line.t3Minutes(), line.t4Minutes(),
				line.t4MinusT3(), line.referenceVersion(), line.referenceResults(), line.deviation(), line.closureResult(),
				line.cause(), line.elapsedOpenMinutes());
	}

	private List<List<Object>> methodology() {
		List<List<Object>> rows = new ArrayList<>();
		ManagementReportPdfRenderer.methodology().forEach(note -> rows.add(row(note)));
		ManagementLabels.EXTERNAL.forEach(note -> rows.add(row(note)));
		rows.add(row(ManagementLabels.LEGEND));
		return rows;
	}

	private static String yesNo(boolean value) {
		return value ? "Sí" : "No";
	}

	/** Una fila; los nulos quedan como celda vacia. */
	private static List<Object> row(Object... values) {
		return new ArrayList<>(Arrays.asList(values));
	}
}
