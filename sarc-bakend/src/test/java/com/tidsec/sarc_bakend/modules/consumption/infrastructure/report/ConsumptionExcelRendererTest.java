package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.internal.PackagePropertiesPart;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.BeanUtils;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionReport;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.RankedEquipment;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** Ruta NATIVO de Excel y PDF con 366 dias, nulos, ceros y empates sobre el mismo informe. */
class ConsumptionExcelRendererTest {

	private static final int DAYS = 366;

	static CorporateBranding branding() throws Exception {
		return BeanUtils.instantiateClass(CorporateBranding.class.getDeclaredConstructor(OperatingZone.class),
				new OperatingZone(ZoneId.of("America/Guayaquil")));
	}

	static ConsumptionReport report(boolean operational) {
		var ranked = new ArrayList<RankedEquipment>();
		int[] energy = { 90, 80, 70, 60, 60, 60 };
		int[] positions = { 1, 2, 3, 4, 4, 4 };
		for (int i = 0; i < energy.length; i++) {
			ranked.add(new RankedEquipment(new EquipmentReference(new UUID(0, i + 1), ChargingNetwork.JAC,
					InfrastructureType.CHARGING_STATION, "QA Equipo " + (i + 1) + " con nombre extenso para etiquetas", "Q" + i),
					1, BigDecimal.valueOf(energy[i]), positions[i]));
		}
		var days = new ArrayList<DailyEnergy>();
		LocalDate start = LocalDate.of(2026, 1, 1);
		for (int day = 0; day < DAYS; day++) {
			BigDecimal value = day == 10 ? null : day == 11 ? BigDecimal.ZERO : new BigDecimal("12.345");
			days.add(new DailyEnergy(start.plusDays(day), day == 12 ? 0 : 1, value, day == 10 ? 1 : 0));
		}
		var official = new EnergyTotals(DAYS, new BigDecimal("4000"), 6, 1);
		var noReading = new EnergyTotals(2, null, 1, 2);
		EnergyTotals restricted = operational ? new EnergyTotals(9, new BigDecimal("99"), 1, 0) : null;
		Instant now = Instant.parse("2026-10-31T02:12:00Z");
		var summary = new ConsumptionSummary(start, start.plusDays(DAYS), official, BigDecimal.TEN, ranked, 0, days,
				Map.of(ChargingNetwork.JAC, official, ChargingNetwork.KIA, noReading, ChargingNetwork.CONECTZ, EnergyTotals.EMPTY),
				Map.of(SessionSource.ENERLINK, official, SessionSource.WALLBOX, EnergyTotals.EMPTY),
				Map.of(InfrastructureType.CHARGING_STATION, official, InfrastructureType.CHARGING_POINT, EnergyTotals.EMPTY),
				restricted, restricted, restricted, restricted, restricted, EnergyTotals.EMPTY, now);
		return new ConsumptionReport("Periodo 01/01/2026 al 01/01/2027", now, summary, ranked, List.of(), List.of(), Map.of());
	}

	/** Todas las partes del paquete: hojas ocultas, nombres, dibujos y caches incluidos. */
	static String packageText(byte[] bytes) throws Exception {
		StringBuilder text = new StringBuilder();
		try (var workbook = OPCPackage.open(new ByteArrayInputStream(bytes))) {
			for (var part : workbook.getParts()) {
				if (part instanceof PackagePropertiesPart) {
					continue;
				}
				try (var content = part.getInputStream()) {
					text.append(new String(content.readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		}
		return text.toString();
	}

	@Test
	void nativeChartsCoverSixSeriesWithBlanksForUnknownEnergy() throws Exception {
		byte[] bytes = new ConsumptionExcelRenderer(branding()).render(report(false));
		Files.write(Path.of("target/feature004-gerencial.xlsx"), bytes);
		Files.write(Path.of("target/feature004-operador.xlsx"),
				new ConsumptionExcelRenderer(branding()).render(report(true)));

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			var sheet = book.getSheet("Gráficos");
			assertThat(sheet.getDrawingPatriarch().getCharts()).hasSize(6);
			var unknown = sheet.getRow(5 + 10).getCell(1);
			assertThat(unknown == null || unknown.getCellType() == CellType.BLANK).isTrue();
			assertThat(sheet.getRow(5 + 11).getCell(1).getNumericCellValue()).isZero();
			assertThat(sheet.getRow(5 + DAYS - 1).getCell(1).getNumericCellValue()).isEqualTo(12.345);
			assertThat(sheet.getRow(5 + 5).getCell(6).getStringCellValue()).startsWith("4. QA Equipo 6");
		}
		String charts = packageText(bytes);
		assertThat(charts).contains("dispBlanksAs val=\"gap\"", "Gráficos", "smooth val=\"false\"", "varyColors val=\"false\"")
				.doesNotContain("smooth val=\"true\"", "varyColors val=\"true\"");
		assertThat(charts).doesNotContain("Pendientes de vincular", "Energía rechazada", "Excluidas");
	}

	@Test
	void operatorsKeepOperationalSheetsNextToTheCharts() throws Exception {
		byte[] bytes = new ConsumptionExcelRenderer(branding()).render(report(true));

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			assertThat(book.getSheet("Excluidas")).isNotNull();
			assertThat(book.getSheet("Gráficos").getDrawingPatriarch().getCharts()).hasSize(6);
		}
		assertThat(packageText(bytes)).contains("Pendientes de vincular (sesiones)");
	}

	@Test
	void approvedAboveThresholdStaysOnlyInOperationalSummary() throws Exception {
		assertThat(packageText(new ConsumptionExcelRenderer(branding()).render(report(false))))
				.doesNotContain("Aprobadas sobre umbral");
		assertThat(packageText(new ConsumptionExcelRenderer(branding()).render(report(true))))
				.contains("Aprobadas sobre umbral (sesiones, incluidas en oficiales)");
	}

	@Test
	void pdfDrawsEverySegmentAndEveryTieWithoutOperationalContent() throws Exception {
		byte[] bytes = new ConsumptionPdfRenderer(branding()).render(report(false));
		Files.write(Path.of("target/feature004-gerencial.pdf"), bytes);
		Files.write(Path.of("target/feature004-operador.pdf"), new ConsumptionPdfRenderer(branding()).render(report(true)));

		try (var reader = new PdfReader(bytes)) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
			}
			assertThat(all.toString()).contains("Energía diaria (kWh)", "Sesiones diarias (sesiones)",
					"Top 5 equipos por energía (kWh)", "Energía por red (kWh)", "Energía por plataforma (kWh)",
					"Energía por tipo (kWh)", "tramo 6 de 6", "4. QA Equipo 6", "Sin sesiones", "Sin datos");
			assertThat(all.toString()).doesNotContain("Fuera de los indicadores oficiales", "Energía rechazada");
		}
	}
}
