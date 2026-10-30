package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.ConsumptionReportRenderer;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionReport;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.RankedEquipment;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporateWorkbook;
import com.tidsec.sarc_bakend.shared.report.DomainLabels;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Excel de consumo (PRD RF-003.15): resumen, desglose diario y por equipo, transacciones oficiales
 * y, en hoja aparte, las excluidas. Las cifras van como numeros para poder verificarlas.
 */
@Component
class ConsumptionExcelRenderer implements ConsumptionReportRenderer {

	private static final List<String> SESSION_HEADERS = List.of("ID sesión", "Fuente", "Inicio", "Término",
			"Día de conteo", "kWh", "Equipo", "Código", "Red", "Tipo", "Activo (origen)", "Cargador (origen)",
			"Serie (origen)", "Conector (origen)");

	private final CorporateBranding branding;
	private final DateTimeFormatter dateTime;

	ConsumptionExcelRenderer(CorporateBranding branding) {
		this.branding = branding;
		this.dateTime = branding.dateTimeFormatter();
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] render(ConsumptionReport report) {
		List<String> excludedHeaders = new ArrayList<>(SESSION_HEADERS);
		excludedHeaders.add("Estado");
		return CorporateWorkbook.create(branding, report.scope(), report.cutOff())
				.sheet("Resumen", "Consumo de energía - indicadores", List.of("Indicador", "Valor"),
						summary(report.summary()))
				.sheet("Distribución", "Energía oficial por red, plataforma y tipo",
						List.of("Dimensión", "Categoría", "Sesiones", "kWh"),
						ConsumptionLabels.distribution(report.summary()).stream()
								.map(share -> List.<Object>of(share.dimension(), share.category(), share.sessions(),
										energy(share.energyKwh())))
								.toList())
				.sheet("Diario", "Consumo por día de inicio", List.of("Fecha", "Sesiones", "kWh"),
						report.summary().daily().stream()
								.map(day -> List.<Object>of(ConsumptionLabels.DAY.format(day.date()), day.sessions(),
										energy(day.energyKwh())))
								.toList())
				.sheet("Por equipo", "Consumo por equipo", List.of("#", "Equipo", "Código", "Red", "Tipo", "Sesiones",
						"kWh", "Id interno"), ranking(report.ranking()))
				.sheet("Transacciones", "Transacciones validadas", SESSION_HEADERS,
						report.official().stream().map(view -> session(view, false)).toList())
				.sheet("Excluidas", "Pendientes de vincular y pruebas (no suman)", excludedHeaders,
						report.excluded().stream().map(view -> session(view, true)).toList())
				.finish();
	}

	private static List<List<Object>> summary(ConsumptionSummary summary) {
		return List.of(
				List.of(ConsumptionLabels.energyTitle(summary.official()), energy(summary.official().energyKwh())),
				List.of("Sesiones", summary.official().sessions()),
				List.of("Sesiones sin energía informada", summary.official().sessionsWithoutEnergy()),
				List.of("Equipos con actividad", summary.official().equipment()),
				List.of("Equipos fuera del ranking sin energía informada", summary.equipmentWithoutEnergy()),
				List.of("kWh por sesión con lectura", energy(summary.averageKwhPerSession())),
				List.of("Pendientes de vincular (sesiones)", summary.pendingLink().sessions()),
				List.of("Pendientes de vincular (kWh)", energy(summary.pendingLink().energyKwh())),
				List.of("Por aprobar (sesiones)", summary.awaitingApproval().sessions()),
				List.of("Por aprobar (kWh)", energy(summary.awaitingApproval().energyKwh())),
				List.of("Pruebas (sesiones)", summary.test().sessions()),
				List.of("Pruebas (kWh)", energy(summary.test().energyKwh())));
	}

	private static List<List<Object>> ranking(List<RankedEquipment> ranking) {
		List<List<Object>> rows = new ArrayList<>();
		for (int index = 0; index < ranking.size(); index++) {
			RankedEquipment item = ranking.get(index);
			rows.add(List.of(index + 1, ConsumptionLabels.name(item.equipment()),
					ConsumptionLabels.code(item.equipment()), ConsumptionLabels.network(item.equipment()),
					item.equipment() == null ? "" : DomainLabels.of(item.equipment().type()), item.sessions(),
					item.energyKwh(), item.equipment() == null ? "" : item.equipment().id().toString()));
		}
		return rows;
	}

	private List<Object> session(SessionView view, boolean withState) {
		EnergySession session = view.session();
		SessionReading reading = session.reading();
		List<Object> row = new ArrayList<>(List.of(session.getExternalSessionId(), ConsumptionLabels.of(session.getSource()),
				dateTime.format(session.getStartedAt()), ended(session),
				ConsumptionLabels.DAY.format(session.getStartDate()), reported(session.getEnergyKwh()),
				ConsumptionLabels.name(view.equipment()), ConsumptionLabels.code(view.equipment()),
				ConsumptionLabels.network(view.equipment()), DomainLabels.of(session.getInfrastructureType()),
				text(reading.site()), text(reading.charger()), text(reading.serial()), text(reading.connector())));
		if (withState) {
			row.add(ConsumptionLabels.of(session.getState()));
		}
		return row;
	}

	/** Una cifra se escribe como numero; la falta de lecturas, como texto, nunca como 0. */
	private static Object energy(BigDecimal value) {
		return value == null ? ConsumptionLabels.NO_DATA : value;
	}

	private static Object reported(BigDecimal value) {
		return value == null ? ConsumptionLabels.NOT_REPORTED : value;
	}

	private String ended(EnergySession session) {
		return session.getEndedAt() == null ? ConsumptionLabels.NOT_REPORTED : dateTime.format(session.getEndedAt());
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
