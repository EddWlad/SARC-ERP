package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;

/** PDF gerencial de intermitencias: las secciones de contracts/reports.md y lo que nunca debe contener. */
class IntermittencyPdfRendererTest {

	private final IntermittencyPdfRenderer renderer = new IntermittencyPdfRenderer(IntermittencyReportSamples.branding());

	private String text(IntermittencyReport report) throws Exception {
		try (var reader = new PdfReader(renderer.render(report))) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append(' ');
			}
			return all.toString().replaceAll("\\s+", " ");
		}
	}

	@Test
	void hasTheIdentityTheTitleThePeriodTheZoneAndTheCutOff() throws Exception {
		String text = text(IntermittencyReportSamples.report(false));

		assertThat(text).contains("Informe gerencial de intermitencias · Red KIA")
				.contains("Primera quincena · 01/10/2026 al 15/10/2026").contains("America/Guayaquil")
				.contains("Fecha de corte:");
		// Los dos logos van como imagen en el encabezado: el documento los lleva embebidos.
		byte[] pdf = renderer.render(IntermittencyReportSamples.report(false));
		assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("/Image");
	}

	@Test
	void theExecutiveSummaryUsesAccumulatedHoursInTheModelOfTheTemplate() throws Exception {
		IntermittencyReport report = IntermittencyReportSamples.report(false);
		var official = report.summary().figures().official();

		String text = text(report);

		assertThat(text).contains("Resumen ejecutivo").contains("Durante el período se registraron 3 cargadores afectados")
				.contains("en " + official.pointDays() + " días-equipo")
				.contains("con " + official.windows() + " ventanas completas")
				.contains(IntermittencyLabels.hours(official.minutes()) + " de tiempo fuera de línea confirmado")
				.contains("El cargador con más tiempo fuera de línea fue QA Punto A");
		// Las horas se acumulan: 1.632 minutos son 27:12, no se reinician a las 24.
		assertThat(official.minutes()).isEqualTo(1632 + 120 + 3);
		assertThat(IntermittencyLabels.hours(1632)).isEqualTo("27:12");
	}

	@Test
	void showsTheIndicatorCardsTheTrendTheTopAndThePies() throws Exception {
		String text = text(IntermittencyReportSamples.report(false));

		assertThat(text).contains("Tiempo confirmado fuera de línea").contains("Cargadores afectados")
				.contains("Ventanas completas").contains("Días-equipo").contains("Equipos recurrentes (3 o más días)")
				.contains("Promedio por ventana").contains("Tendencia diaria").contains("Tiempo confirmado por día (h:mm)")
				.contains("Ventanas por día (cantidad)").contains("Top 10 de cargadores por tiempo fuera de línea")
				.contains("QA Punto A").contains("900001").contains("Tiempo por duración de ventana")
				.contains("Menos de 5 min").contains("4 h o más").contains("Concentración del tiempo")
				.contains("Equipos recurrentes frente a no recurrentes").contains("Recurrentes (3 o más días)");
		// La leyenda de cada pastel trae el tiempo y el porcentaje, no solo el color.
		assertThat(text).containsPattern("\\d+:\\d{2} h · \\d+,\\d %");
	}

	@Test
	void theTieNoteAppearsOnlyWhenTheTopExceedsTenAndTheNoteAboutMidnightOnlyWhenItApplies() throws Exception {
		assertThat(text(IntermittencyReportSamples.report(false))).doesNotContain("incluye más de diez cargadores")
				.doesNotContain("cruza la medianoche");

		IntermittencyReport tied = IntermittencyReportSamples.withTies(false);
		assertThat(tied.summary().figures().top()).hasSize(12);
		assertThat(text(tied)).contains("El Top 10 incluye más de diez cargadores por empate en el décimo lugar")
				.contains("Top 10 (con empates)");
	}

	@Test
	void methodologyNotesExplainDefinitionsUnitsBandsTiesAndTheSeparationFromUptime() throws Exception {
		String text = text(IntermittencyReportSamples.report(false));

		assertThat(text).contains("Notas de metodología").contains("Solo suman las ventanas confirmadas")
				.contains("el sistema lo guarda con el fin exclusivo").contains("horas acumuladas y minutos (h:mm)")
				.contains("Franjas de duración").contains("más ventanas, más días afectados y nombre actual")
				.contains("no sustituyen las mediciones de uptime");
	}

	@Test
	void whatIsOutsideTheOfficialFiguresIsReportedApartAsQuantities() throws Exception {
		String text = text(IntermittencyReportSamples.report(false));

		assertThat(text).contains("Fuera de los indicadores oficiales").contains("Eventos abiertos")
				.contains("provisional").contains("Total por revisar").contains("Ventanas por revisar (4 h o más)")
				.contains("Ventanas de prueba").contains("Filas por vincular").contains("Filas en conflicto")
				.contains("no suman en los totales");
		assertThat(text).doesNotContain("Anexo:");
	}

	@Test
	void theOperationalAnnexIsOnlyForTheReportThatAllowsIt() throws Exception {
		String officialOnly = text(IntermittencyReportSamples.report(false));
		String withOperations = text(IntermittencyReportSamples.report(true));

		assertThat(officialOnly).doesNotContain("Anexo: ventanas fuera de lo oficial")
				.doesNotContain("Anexo: filas pendientes");
		assertThat(withOperations).contains("Anexo: ventanas fuera de lo oficial").contains("Abierta")
				.contains("Por revisar").contains("Anexo: filas pendientes de lotes confirmados")
				.contains("Fila 4 de un lote confirmado");
	}

	@Test
	void neverContainsUidPukOrAnOcppUrl() throws Exception {
		for (boolean operational : new boolean[] { false, true }) {
			String text = text(IntermittencyReportSamples.report(operational)).toLowerCase();

			assertThat(text).doesNotContainPattern("\\buid\\b").doesNotContainPattern("\\bpuk\\b").doesNotContain("ocpp")
					.doesNotContain("ws://").doesNotContain("wss://");
		}
	}

	@Test
	void withoutConfirmedTimeItSaysSoAndDrawsNoSeries() throws Exception {
		String text = text(IntermittencyReportSamples.empty(false));

		assertThat(text).contains("No hay intermitencias confirmadas en el período.")
				.doesNotContain("Tiempo confirmado por día").doesNotContain("Top 10 de cargadores")
				.doesNotContain("Tiempo por duración de ventana");
		// Lo que no es oficial y la metodologia se informan igual.
		assertThat(text).contains("Eventos abiertos").contains("Notas de metodología");
	}

	@Test
	void theAvailabilityIsASeparateSectionOnlyForTheReportOfWhoHasThePermission() throws Exception {
		String without = text(IntermittencyReportSamples.report(true));
		String with = text(IntermittencyReportSamples.withAvailability(true));

		assertThat(without).doesNotContain("Disponibilidad estimada").doesNotContain("Cobertura");
		assertThat(with).contains("Disponibilidad estimada (prueba)")
				.contains("Disponibilidad estimada por intermitencias (prueba)")
				.contains("no es una medición de uptime ni de Enerlink")
				.contains("Registro manual de intermitencias (Kibana)").contains("99,50 %").contains("1 de 15 días")
				.contains("95,00 %").contains("100,00 %").contains("Sin datos")
				.contains("QA Punto Desconectado").contains("Desconectado: instalado sin servicio")
				.contains("Días sin cubrir por filas pendientes de vincular")
				.contains("Limitaciones de la disponibilidad estimada").contains("La resolución es el minuto.");
	}

	@Test
	void theAvailabilityComesAfterTheOfficialFiguresAndIsNeverMixedWithTheTopOrTheMethodology() throws Exception {
		String text = text(IntermittencyReportSamples.withAvailability(false));

		assertThat(text.indexOf("Disponibilidad estimada (prueba)"))
				.isGreaterThan(text.indexOf("Fuera de los indicadores oficiales"));
		String top = text.substring(text.indexOf("Top 10 de cargadores"), text.indexOf("Tiempo por duración de ventana"));
		assertThat(top).doesNotContain("Disponibilidad").doesNotContain("Cobertura");
		String methodology = text.substring(text.indexOf("Notas de metodología"),
				text.indexOf("Fuera de los indicadores oficiales"));
		assertThat(methodology).doesNotContain("Disponibilidad estimada");
	}
}
