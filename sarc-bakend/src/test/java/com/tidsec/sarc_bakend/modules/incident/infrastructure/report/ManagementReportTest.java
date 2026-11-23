package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.BeanUtils;

import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.EpisodeFact;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt.Value;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Judgment;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Measure;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Result;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** FR-080 a FR-085: secciones del PDF, leyenda de referencias, fuentes externas sin cifras y hojas del Excel. */
class ManagementReportTest {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");
	private static final Instant CUT_OFF = Instant.parse("2026-11-22T20:30:00Z");

	private static CorporateBranding branding() {
		try {
			return BeanUtils.instantiateClass(CorporateBranding.class.getDeclaredConstructor(OperatingZone.class),
					new OperatingZone(ZONE));
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Map<MilestoneKind, Value> milestones(int t1, int t3, int t4) {
		Map<MilestoneKind, Value> map = new EnumMap<>(MilestoneKind.class);
		map.put(MilestoneKind.T0, new Value(MilestoneStatus.RECORDED, T0));
		map.put(MilestoneKind.T1, new Value(MilestoneStatus.RECORDED, T0.plusSeconds(t1 * 60L)));
		map.put(MilestoneKind.T2, new Value(MilestoneStatus.NOT_APPLICABLE, null));
		map.put(MilestoneKind.T3, new Value(MilestoneStatus.RECORDED, T0.plusSeconds(t3 * 60L)));
		map.put(MilestoneKind.T4, new Value(MilestoneStatus.RECORDED, T0.plusSeconds(t4 * 60L)));
		return map;
	}

	private static ManagementData data(boolean withEpisodes) {
		List<EpisodeFact> facts = new ArrayList<>();
		List<ManagementData.EpisodeLine> lines = new ArrayList<>();
		if (withEpisodes) {
			facts.add(new EpisodeFact(UUID.randomUUID(), "TICKET-9001", 1, false, ClosureResult.RECOVERED,
					IncidentType.DISCONNECTION, ElementClass.EQUIPMENT, "KIA", UUID.randomUUID(), "Equipo Norte",
					Priority.HIGH, ReportOrigin.PLATFORM, T0, milestones(4, 200, 220), null,
					List.of(new Judgment(Measure.VALIDATION, 5, false, Result.WITHIN),
							new Judgment(Measure.RECOVERY, 180, true, Result.OUTSIDE)),
					"Falla del proveedor"));
			lines.add(new ManagementData.EpisodeLine("TICKET-9001", 1, "Desconexión", "Alta", "Equipo Norte", "KIA", "CLOSED",
					T0.toString(), T0.plusSeconds(240).toString(), null, T0.plusSeconds(12000).toString(),
					T0.plusSeconds(13200).toString(), "R", "R*", "N/A", "R", "R", 4, null, 200, 220, 20, "V1",
					"VALIDATION=WITHIN; RECOVERY=OUTSIDE", "Falla del proveedor", "RECOVERED", "CONFIRMED", null));
		}
		return new ManagementData(CUT_OFF, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null, null,
				IncidentKpiCalculator.compute(facts, List.of(), ZONE), withEpisodes ? 0 : 0, List.of(), lines, List.of(),
				List.of(), List.of(), withEpisodes ? "V1" : "");
	}

	private static String pdfText(byte[] bytes) throws Exception {
		try (var reader = new PdfReader(bytes)) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
			}
			return all.toString();
		}
	}

	@Test
	void thePdfHasTheSectionsTheLegendTheVersionAndNoAvailabilityFigures() throws Exception {
		String text = pdfText(new ManagementReportPdfRenderer(branding()).render(data(true)));

		assertThat(text).contains("Gestión de incidencias", "1. Resumen ejecutivo", "2. Indicadores de tiempo",
				"3. Referencias operativas", "4. Gráficos (sin ponderar)", "5. Repartos", "6. Visitas técnicas",
				"7. Detalle por ticket y episodio", "8. Fuentes externas", "9. Metodología");
		assertThat(text).contains("Referencia operativa, no SLA contractual", "Versión de referencias: V1");
		assertThat(text).contains("Falla del proveedor", "Período: 01/10/2026 al 31/10/2026");
		assertThat(text).contains("Uptime", "Intermitencias", "no se recalculan aquí");
		assertThat(text).doesNotContain("98 %").doesNotContainIgnoringCase("recurrente\"");
		assertThat(text).contains("(n = 1)");
	}

	@Test
	void anEmptyPeriodOmitsTheChartsAndSaysThereAreNoData() throws Exception {
		String text = pdfText(new ManagementReportPdfRenderer(branding()).render(data(false)));

		assertThat(text).contains("Gestión de incidencias", "Sin datos en el período", "8. Fuentes externas");
		assertThat(text).doesNotContain("Episodios por tipo", "Episodios por red", "Equipos con más episodios");
	}

	@Test
	void theExcelHasTheSevenSheetsAndAnEmptyTimeIsEmptyNotZero() throws Exception {
		byte[] xlsx = new ManagementReportExcelRenderer(branding()).render(data(true));

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			List<String> names = new ArrayList<>();
			book.forEach(sheet -> names.add(sheet.getSheetName()));
			assertThat(names).containsExactly("Resumen", "Tickets", "Episodios", "Bitácora", "Comunicaciones", "Visitas",
					"Metodología");
			var episodes = book.getSheet("Episodios");
			var row = episodes.getRow(5);
			assertThat(row.getCell(0).getStringCellValue()).isEqualTo("TICKET-9001");
			assertThat(row.getCell(17).getNumericCellValue()).isEqualTo(4.0);
			assertThat(row.getCell(18)).as("T2−T0 sin hito: vacío, nunca cero").isNull();
			assertThat(book.getSheet("Metodología").getRow(5).getCell(0).getStringCellValue()).contains("Alcance");
		}
	}
}
