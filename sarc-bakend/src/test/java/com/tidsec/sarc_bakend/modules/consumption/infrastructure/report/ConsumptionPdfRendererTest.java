package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

class ConsumptionPdfRendererTest {
	@Test
	void aLongPeriodStillShowsEnergyExceptionsInTheOpeningSummary() throws Exception {
		ZoneId zone = ZoneId.of("America/Guayaquil");
		var branding = BeanUtils.instantiateClass(CorporateBranding.class.getDeclaredConstructor(OperatingZone.class),
				new OperatingZone(zone));
		var ranked = new ArrayList<RankedEquipment>();
		int[] energy = {1600, 90, 80, 70, 70, 70, 60};
		int[] positions = {1, 2, 3, 4, 4, 4, 7};
		for (int i = 0; i < 7; i++) ranked.add(new RankedEquipment(new EquipmentReference(new UUID(0, i + 1),
				ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION, "QA Equipo " + (i + 1), "QA-" + i),
				i == 0 ? 2 : 1, BigDecimal.valueOf(energy[i]), positions[i]));
		var days = new ArrayList<DailyEnergy>();
		for (int day = 1; day <= 30; day++) days.add(new DailyEnergy(LocalDate.of(2026, 10, day),
				day == 21 ? 8 : 0, BigDecimal.valueOf(day == 21 ? 2040 : 0), 0));
		var official = new EnergyTotals(8, new BigDecimal("2040"), 7, 0);
		var high = new EnergyTotals(1, new BigDecimal("1500"), 1, 0);
		Instant now = Instant.parse("2026-10-31T02:12:00Z");
		var summary = new ConsumptionSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), official,
				new BigDecimal("255"), ranked.subList(0, 6), 0, days,
				Map.of(ChargingNetwork.JAC, official, ChargingNetwork.KIA, EnergyTotals.EMPTY, ChargingNetwork.CONECTZ, EnergyTotals.EMPTY),
				Map.of(SessionSource.ENERLINK, new EnergyTotals(7, new BigDecimal("540"), 7, 0), SessionSource.WALLBOX, high),
				Map.of(InfrastructureType.CHARGING_STATION, official, InfrastructureType.CHARGING_POINT, EnergyTotals.EMPTY),
				EnergyTotals.EMPTY, EnergyTotals.EMPTY, EnergyTotals.EMPTY, EnergyTotals.EMPTY, high, high, now);
		byte[] bytes = new ConsumptionPdfRenderer(branding).render(new ConsumptionReport(
				"Periodo 01/10/2026 al 30/10/2026", now, summary, ranked, List.of(), List.of(), Map.of()));
		Files.write(Path.of("target/feature003-final-render.pdf"), bytes);
		try (var reader = new PdfReader(bytes)) {
			String firstPage = new PdfTextExtractor(reader).getTextFromPage(1);
			assertThat(firstPage).contains("Revisión energética", "Energía rechazada", "Aprobadas sobre umbral", "1.500,00");
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) all.append(new PdfTextExtractor(reader).getTextFromPage(page));
			assertThat(all.toString()).contains("por empate", "QA Equipo 7", "Ranking por equipo (7)");
		}
	}
}
