package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.BeanUtils;

import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.UnitState;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** Reportes armados a mano para probar los renderers sin base de datos. */
final class UptimeReportSamples {

	static final Instant CUT_OFF = Instant.parse("2026-11-01T17:00:00Z");
	static final Instant WEEK = Instant.parse("2026-10-12T05:00:00Z");
	static final MeasurableConnector CONNECTOR = new MeasurableConnector(UUID.randomUUID(), 1, "CCS2", "CCS2", true,
			UUID.randomUUID(), "Electrolinera Actual", "EL-01", ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
			"JAC", AdministrativeStatus.ACTIVE);

	private UptimeReportSamples() {
	}

	static CorporateBranding branding() throws ReflectiveOperationException {
		return BeanUtils.instantiateClass(CorporateBranding.class.getDeclaredConstructor(OperatingZone.class),
				new OperatingZone(ZoneId.of("America/Guayaquil")));
	}

	private static UptimeCalculator.Kpis kpis(String percent) {
		return new UptimeCalculator.Kpis(new BigDecimal(percent), 1440, new BigDecimal("0.65"), BigDecimal.ZERO, 1, 0, 1, 2);
	}

	private static UptimeReport.Section section(PeriodType type, String percent) {
		Instant to = WEEK.plus(type.nominal());
		UptimeCalculator.ConnectorResult connector = new UptimeCalculator.ConnectorResult(1, CONNECTOR,
				new BigDecimal(percent), 1440, 39, 0, WEEK, to);
		UptimeCalculator.EquipmentResult equipment = new UptimeCalculator.EquipmentResult(CONNECTOR,
				new BigDecimal(percent), 1440, 39, 0, 1, 2);
		return new UptimeReport.Section(type, WEEK, to, kpis(percent), List.of(connector), List.of(equipment));
	}

	private static UptimeCalculator.Coverage coverage() {
		return new UptimeCalculator.Coverage(new BigDecimal("50.00"), 1, 2,
				Map.of(UnitState.VALIDATED, 1, UnitState.PENDING_REVIEW, 1));
	}

	static UptimeReport full() {
		return new UptimeReport("Alcance de prueba", CUT_OFF,
				new UptimeView.ResolvedPeriod(OpeningPeriodPolicy.Kind.OFFICIAL, PeriodType.EXACT_WEEK, WEEK,
						WEEK.plus(PeriodType.EXACT_WEEK.nominal()), "America/Guayaquil"),
				section(PeriodType.EXACT_WEEK, "97.29"), section(PeriodType.ROLLING_7D, "88.10"),
				List.of(new UptimeView.TrendPoint(LocalDate.of(2026, 10, 5), new BigDecimal("96.00")),
						new UptimeView.TrendPoint(LocalDate.of(2026, 10, 12), null),
						new UptimeView.TrendPoint(LocalDate.of(2026, 10, 19), new BigDecimal("97.29"))),
				List.of(new UptimeReport.WeekResult(WEEK, kpis("97.29"))), coverage(),
				List.of(new UptimeCalculator.Exclusion(CONNECTOR, UnitState.NO_DATA)), 0);
	}

	static UptimeReport empty() {
		return new UptimeReport("Alcance vacio", CUT_OFF,
				new UptimeView.ResolvedPeriod(OpeningPeriodPolicy.Kind.NONE, null, null, null, "America/Guayaquil"), null,
				null, List.of(), List.of(), new UptimeCalculator.Coverage(null, 0, 0, Map.of()), List.of(), 0);
	}

	static String text(byte[] pdf) throws IOException {
		try (PdfReader reader = new PdfReader(pdf)) {
			StringBuilder text = new StringBuilder();
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				text.append(extractor.getTextFromPage(page)).append('\n');
			}
			return text.toString();
		}
	}

	static List<String> sheets(byte[] excel) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			List<String> names = new ArrayList<>();
			workbook.forEach(sheet -> names.add(sheet.getSheetName()));
			return names;
		}
	}

	/** Celdas formateadas de todas las hojas, a partir de la fila de encabezados. */
	static String cells(byte[] excel) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			DataFormatter formatter = new DataFormatter();
			StringBuilder text = new StringBuilder();
			for (Sheet sheet : workbook) {
				for (Row row : sheet) {
					if (row.getRowNum() >= 4) {
						row.forEach(cell -> text.append(formatter.formatCellValue(cell)).append('|'));
						text.append('\n');
					}
				}
			}
			return text.toString();
		}
	}
}
