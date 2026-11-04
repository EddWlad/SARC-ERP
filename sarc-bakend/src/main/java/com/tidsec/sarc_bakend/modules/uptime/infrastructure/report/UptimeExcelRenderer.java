package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeReportRenderer;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeView;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporateWorkbook;
import com.tidsec.sarc_bakend.shared.report.DomainLabels;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Excel de disponibilidad. Las cifras van como numeros; la falta de cifra, como texto. */
@Component
class UptimeExcelRenderer implements UptimeReportRenderer {

	private static final List<String> PERIOD_HEADERS = List.of("Tipo", "Desde", "Hasta", "Uptime (%)",
			"Fuera de línea (h)", "Con falla (h)", "Equipos evaluados", "Equipos sin datos");
	private static final String OFFICIAL = "Oficial";
	private static final String PROVISIONAL = "PROVISIONAL";

	private final CorporateBranding branding;
	private final DateTimeFormatter dateTime;

	UptimeExcelRenderer(CorporateBranding branding) {
		this.branding = branding;
		this.dateTime = branding.dateTimeFormatter();
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] render(UptimeReport report) {
		return CorporateWorkbook.create(branding, report.scope(), report.cutOff())
				.sheet("Resumen", "Uptime y disponibilidad - indicadores", List.of("Indicador", "Valor"), summary(report))
				.sheet("Semanas oficiales", "Cifras oficiales por semana exacta",
						List.of("Semana desde", "Uptime (%)", "Fuera de línea (h)", "Con falla (h)", "Equipos evaluados",
								"Conectores incluidos", "Conectores evaluables"), weeks(report))
				.sheet("Provisionales", "Ventana provisional: no es una semana oficial", PERIOD_HEADERS, provisional(report))
				.sheet("Por conector", "Resultado por conector, de menor a mayor uptime",
						List.of("Período", "#", "Equipo", "Código", "Red", "Tipo", "Conector", "Uptime (%)",
								"Fuera de línea (min)", "Con falla (min)", "Evaluado (min)"), connectors(report))
				.sheet("Por equipo", "Promedio de conectores por equipo",
						List.of("Período", "Equipo", "Código", "Red", "Tipo", "Uptime (%)", "Conectores incluidos",
								"Conectores evaluables", "Fuera de línea (min)", "Con falla (min)"), equipment(report))
				.sheet("Cobertura y exclusiones", "Cantidades por estado y conectores sin cifra",
						List.of("Estado", "Cantidad", "Equipo", "Código", "Conector"), coverage(report))
				.charts("Gráficos", "Gráficos oficiales: datos y representaciones", charts(report))
				.finish();
	}

	private static List<List<Object>> summary(UptimeReport report) {
		List<List<Object>> rows = new ArrayList<>();
		if (report.official() == null) {
			rows.add(List.of("Cifra oficial", UptimeLabels.NO_OFFICIAL));
		}
		else {
			rows.addAll(block("oficial", "oficiales", report.official().kpis()));
		}
		if (report.provisional() != null) {
			rows.add(List.of("Ventana provisional", UptimeLabels.of(report.provisional().periodType())));
			rows.addAll(block("provisional", "provisionales", report.provisional().kpis()));
		}
		rows.add(List.of("Cobertura (%)", value(report.coverage().percent())));
		rows.add(List.of("Metodología", UptimeLabels.FORMULA));
		rows.add(List.of("Promedio por equipo", UptimeLabels.EQUIPMENT_NOTE));
		return rows;
	}

	private static List<List<Object>> block(String singular, String plural, UptimeCalculator.Kpis kpis) {
		return List.of(List.of("Uptime " + singular + " (%)", value(kpis.uptimePercent())),
				List.of("Horas fuera de línea " + plural, kpis.offlineHours()),
				List.of("Horas con falla " + plural, kpis.faultHours()),
				List.of("Equipos evaluados " + plural, kpis.equipmentEvaluated()),
				List.of("Equipos sin datos " + plural, kpis.equipmentWithoutData()));
	}

	private List<List<Object>> weeks(UptimeReport report) {
		return report.weeks().stream().map(week -> List.<Object>of(dateTime.format(week.weekStart()),
				value(week.kpis().uptimePercent()), week.kpis().offlineHours(), week.kpis().faultHours(),
				week.kpis().equipmentEvaluated(), week.kpis().connectorsIncluded(), week.kpis().connectorsInScope())).toList();
	}

	private List<List<Object>> provisional(UptimeReport report) {
		UptimeReport.Section section = report.provisional();
		if (section == null) {
			return List.of();
		}
		UptimeCalculator.Kpis kpis = section.kpis();
		return List.of(List.of(UptimeLabels.of(section.periodType()), dateTime.format(section.from()),
				dateTime.format(section.to()), value(kpis.uptimePercent()), kpis.offlineHours(), kpis.faultHours(),
				kpis.equipmentEvaluated(), kpis.equipmentWithoutData()));
	}

	private static List<List<Object>> connectors(UptimeReport report) {
		List<List<Object>> rows = new ArrayList<>();
		if (report.official() != null) {
			report.official().connectors().forEach(item -> rows.add(connector(OFFICIAL, item)));
		}
		if (report.provisional() != null) {
			report.provisional().connectors().forEach(item -> rows.add(connector(PROVISIONAL, item)));
		}
		return rows;
	}

	private static List<Object> connector(String period, UptimeCalculator.ConnectorResult item) {
		return List.of(period, item.position(), item.connector().equipmentName(), UptimeLabels.code(item.connector()),
				item.connector().network().name(), DomainLabels.of(item.connector().type()),
				UptimeLabels.connector(item.connector()), value(item.uptimePercent()), item.offlineMinutes(),
				item.faultMinutes(), item.evaluatedMinutes());
	}

	private static List<List<Object>> equipment(UptimeReport report) {
		List<List<Object>> rows = new ArrayList<>();
		if (report.official() != null) {
			report.official().equipment().forEach(item -> rows.add(equipment(OFFICIAL, item)));
		}
		if (report.provisional() != null) {
			report.provisional().equipment().forEach(item -> rows.add(equipment(PROVISIONAL, item)));
		}
		return rows;
	}

	private static List<Object> equipment(String period, UptimeCalculator.EquipmentResult item) {
		return List.of(period, item.sample().equipmentName(), UptimeLabels.code(item.sample()),
				item.sample().network().name(), DomainLabels.of(item.sample().type()), value(item.uptimePercent()),
				item.connectorsIncluded(), item.connectorsInScope(), item.offlineMinutes(), item.faultMinutes());
	}

	private static List<List<Object>> coverage(UptimeReport report) {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(List.of("Pendientes de vincular (sin atribuir)", report.pendingLink(), "", "", ""));
		UptimeLabels.STATES.forEach(state -> rows.add(List.of(UptimeLabels.of(state),
				report.coverage().byState().getOrDefault(state, 0), "", "", "")));
		report.exclusions().forEach(item -> rows.add(List.of(UptimeLabels.of(item.state()), "",
				item.connector().equipmentName(), UptimeLabels.code(item.connector()),
				UptimeLabels.connector(item.connector()))));
		return rows;
	}

	private static List<CorporateWorkbook.Chart> charts(UptimeReport report) {
		if (report.trend().isEmpty()) {
			return List.of();
		}
		List<BigDecimal> values = new ArrayList<>();
		report.trend().forEach(point -> values.add(point.uptimePercent()));
		return List.of(new CorporateWorkbook.Chart("Uptime oficial por semana", "%", true, report.trend().stream()
				.map(UptimeView.TrendPoint::weekStart).map(UptimePdfCharts.WEEK::format).toList(), values));
	}

	private static Object value(BigDecimal value) {
		return value == null ? UptimeLabels.NO_DATA : value;
	}
}
