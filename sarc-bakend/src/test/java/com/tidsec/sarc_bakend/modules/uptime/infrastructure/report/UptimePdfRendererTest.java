package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UptimePdfRendererTest {

	private static String render(com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport report)
			throws Exception {
		return UptimeReportSamples.text(new UptimePdfRenderer(UptimeReportSamples.branding()).render(report));
	}

	@Test
	void separatesOfficialAndProvisionalWithLabels() throws Exception {
		String text = render(UptimeReportSamples.full());

		assertThat(text).contains("Semanas oficiales", "PROVISIONAL", "Últimos 7 días", "97,29 %", "88,10 %",
				"Tendencia de semanas oficiales", "Horas fuera de línea y con falla",
				"Cobertura y exclusiones", "Sin datos");
		assertThat(text.indexOf("Semanas oficiales")).isLessThan(text.indexOf("PROVISIONAL"));
	}

	@Test
	void usesCurrentNameAndLabelsEquipmentAverage() throws Exception {
		assertThat(render(UptimeReportSamples.full())).contains("Electrolinera Actual", "EL-01",
				"Promedio de conectores por equipo", "1 de 2");
	}

	@Test
	void emptyReportWarnsAndNeverPrintsZeroPercent() throws Exception {
		String text = render(UptimeReportSamples.empty());

		assertThat(text).contains("No hay cifra semanal oficial", "Sin datos").doesNotContain("0,00 %", "0 %");
	}
}
