package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UptimeExcelRendererTest {

	private static byte[] render(com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport report)
			throws Exception {
		return new UptimeExcelRenderer(UptimeReportSamples.branding()).render(report);
	}

	@Test
	void hasTheSevenContractSheets() throws Exception {
		assertThat(UptimeReportSamples.sheets(render(UptimeReportSamples.full()))).containsExactly("Resumen",
				"Semanas oficiales", "Provisionales", "Por conector", "Por equipo", "Cobertura y exclusiones", "Gráficos");
	}

	@Test
	void labelsPeriodsAndUsesCurrentNames() throws Exception {
		assertThat(UptimeReportSamples.cells(render(UptimeReportSamples.full())).replace(',', '.')).contains("Oficial", "PROVISIONAL",
				"Últimos 7 días", "Electrolinera Actual", "EL-01", "97.29", "88.1");
	}

	@Test
	void emptyReportSaysNoDataNotZero() throws Exception {
		byte[] excel = render(UptimeReportSamples.empty());

		assertThat(UptimeReportSamples.sheets(excel)).hasSize(7);
		assertThat(UptimeReportSamples.cells(excel).replace(',', '.')).contains("No hay cifra semanal oficial", "Sin datos")
				.doesNotContain("0.00");
	}
}
