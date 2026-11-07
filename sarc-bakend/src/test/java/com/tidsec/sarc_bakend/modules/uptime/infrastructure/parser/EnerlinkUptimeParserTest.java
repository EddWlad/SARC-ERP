package com.tidsec.sarc_bakend.modules.uptime.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularSheet;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Formato del informe semanal que se envia por correo (documents/Modelo_Informe_UpTime...): una hoja
 * por grupo, periodo desactualizado en la fila 1, encabezados en la 3, horas con decimales y sin
 * Activo ni Cargando. Nombres y cifras anonimizados.
 */
class EnerlinkUptimeParserTest {

	private static final String MINUTES_HEADER = "ACTIVO\tCARGADOR\tCONECTOR\tDESDE\tHASTA\tDISPONIBLE\tOCUPADO\tCON FALLA\tFUERA DE LINEA\tUPTIME";
	private static final String[] MODEL_HEADER = { "Cargador", "Conector", "Uptime (%)", "Fuera de linea (hrs)",
			"Disponible (hrs)", "En falla (hrs)" };
	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final UptimeRowParser.DeclaredPeriod WEEK = new UptimeRowParser.DeclaredPeriod(
			java.time.LocalDate.of(2026,10,19));

	private final EnerlinkUptimeParser parser = new EnerlinkUptimeParser(new OperatingZone(ZONE));
	private final TabularReader reader = new TabularReader();

	@Test
	void readsEverySheetOfTheWeeklyModelWithTheChosenWeek() throws IOException {
		List<UptimeRowParser.ParsedRow> rows = parser.parse(reader.readSheets(model()), WEEK);

		assertThat(rows).hasSize(3);
		UptimeRowParser.ParsedRow first = rows.getFirst();
		assertThat(first.isValid()).isTrue();
		assertThat(first.sheet()).isEqualTo("Uptime Electrolineras");
		assertThat(first.sheetRow()).isEqualTo(4);
		assertThat(first.rowNumber()).isEqualTo(EnerlinkUptimeParser.SHEET_STRIDE + 4);
		assertThat(first.site()).isEmpty();
		assertThat(first.charger()).isEqualTo("KIA SEDE UNO");
		assertThat(first.periodStart()).isNull();
		assertThat(first.periodEnd()).isNull();
		assertThat(first.values().basis()).isEqualTo(UptimeBasis.REPORTED_PERCENT);
		assertThat(first.values().occupied()).isNull();
		assertThat(first.values().reportedPercent()).isEqualByComparingTo("99.40");
		assertThat(first.values().offline()).isEqualByComparingTo("60");
		assertThat(first.values().available()).isEqualByComparingTo("9960");
		assertThat(first.sourceValues()).containsEntry("offlineHours", "1").doesNotContainKey("periodStart");

		UptimeRowParser.ParsedRow other = rows.getLast();
		assertThat(other.sheet()).isEqualTo("Uptime Ptos. Carga");
		assertThat(other.rowNumber()).isEqualTo(2 * EnerlinkUptimeParser.SHEET_STRIDE + 4);
	}

	@Test
	void roundsHoursToTheNearestMinute() throws IOException {
		UptimeRowParser.ParsedRow row = parser.parse(reader.readSheets(model()), WEEK).get(1);

		// 27,23 h = 1633,8 min y 0,07 h = 4,2 min.
		assertThat(row.values().offline()).isEqualByComparingTo("1634");
		assertThat(row.values().fault()).isEqualByComparingTo("4");
	}

	@Test
	void reportWithChargingTimeKeepsTheFourStates() {
		String text = "Activo\tCargador\tConector\tUptime (%)\tFuera de linea (hrs)\tDisponible (hrs)\tCargando (hrs)"
				+ "\tEn falla (hrs)\nSEDE UNO\tJAC SEDE UNO\tConector 1 - Tipo 2\t93.60\t1\t160\t7\t0";

		UptimeRowParser.ParsedRow row = parser.parse(sheet(text), WEEK).getFirst();

		assertThat(row.values().basis()).isEqualTo(UptimeBasis.STATES);
		assertThat(row.site()).isEqualTo("SEDE UNO");
		assertThat(row.values().occupied()).isEqualByComparingTo("420");
		assertThat(row.sheet()).isNull();
		assertThat(row.rowNumber()).isEqualTo(2);
	}

	@Test
	void withoutWeekNorPeriodColumnsIsRejected() {
		String text = String.join("\t", MODEL_HEADER) + "\nX\tConector 1\t90\t1\t1\t0";

		assertThatThrownBy(() -> parser.parse(sheet(text), null)).isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("UPTIME_PERIOD_MISSING");
	}

	@Test
	void missingColumnNamesTheSheet() throws IOException {
		try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Row head = book.createSheet("Uptime Ptos. Carga").createRow(0);
			head.createCell(0).setCellValue("Cargador");
			head.createCell(1).setCellValue("Conector");
			book.write(out);

			assertThatThrownBy(() -> parser.parse(reader.readSheets(out.toByteArray()), WEEK))
					.isInstanceOf(BusinessRuleException.class).hasMessageContaining("Hoja \"Uptime Ptos. Carga\"")
					.hasMessageContaining("UPTIME");
		}
	}

	@Test
	void keepsMinutesAsTheyArrivedWhenHeadersCarryNoUnit() {
		UptimeRowParser.ParsedRow row = parser.parse(sheet(MINUTES_HEADER
				+ "\nSede A\tCargador 1\t1 - Tipo 2\t30-10-2026 14:00:00\t31-10-2026 14:00:00\t1296\t105\t0\t39\t97.29"),
				null).getFirst();

		assertThat(row.isValid()).isTrue();
		assertThat(row.periodEnd()).isEqualTo(Instant.parse("2026-10-31T19:00:00Z"));
		assertThat(row.values().offline()).isEqualByComparingTo("39");
		assertThat(row.values().reportedPercent()).isEqualByComparingTo("97.29");
		assertThat(row.sourceValues()).containsEntry("offlineMinutes", "39").doesNotContainKey("offlineHours");
	}

	@Test
	void nonNumericTextInvalidatesTheRow() {
		UptimeRowParser.ParsedRow row = parser.parse(sheet(
				MINUTES_HEADER + "\nSede A\tCargador 1\t1\t30-10-2026 14:00:00\t31-10-2026 14:00:00\tmucho\t105\t0\t39\t"),
				null).getFirst();

		assertThat(row.isValid()).isFalse();
		assertThat(row.issues()).extracting("code").contains("INVALID_NUMBER");
	}

	private List<TabularSheet> sheet(String text) {
		return List.of(new TabularSheet("", reader.readText(text)));
	}

	/** Como el original: periodo viejo en la fila 1, encabezados en la 3; una hoja vacia se ignora. */
	private static byte[] model() throws IOException {
		try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			fill(book.createSheet("Uptime Electrolineras"), new Object[] { "KIA SEDE UNO", "Conector 1", 99.4, 1, 166, 0 },
					new Object[] { "KIA SEDE DOS", "Conector 2-CCS2", 83.7, 27.23, 114.46, 0.07 });
			book.createSheet("Vacia");
			fill(book.createSheet("Uptime Ptos. Carga"), new Object[] { "JAC PUNTO", "Conector 1", 100, 0, 168, 0 });
			book.write(out);
			return out.toByteArray();
		}
	}

	private static void fill(Sheet sheet, Object[]... data) {
		sheet.createRow(0).createCell(0).setCellValue("Uptime 01-09-2026 00:00:00 07-09-2026 23:59:59");
		Row head = sheet.createRow(2);
		for (int index = 0; index < MODEL_HEADER.length; index++) {
			head.createCell(index).setCellValue(MODEL_HEADER[index]);
		}
		for (int line = 0; line < data.length; line++) {
			Row row = sheet.createRow(3 + line);
			for (int index = 0; index < data[line].length; index++) {
				Object value = data[line][index];
				if (value instanceof Number number) {
					row.createCell(index).setCellValue(number.doubleValue());
				}
				else {
					row.createCell(index).setCellValue((String) value);
				}
			}
		}
	}
    @Test void declaredWeekDoesNotOverwriteRealSourceBounds() {
        String text="Activo\tCargador\tConector\tDesde\tHasta\tDisponible\tOcupado\tCon falla\tFuera de linea\tUptime\n"
            +"Sede\tCargador\t1\t28-10-2026 08:06:20\t04-11-2026 08:06:20\t9815\t31\t0\t234\t97.68";
        var row=parser.parse(sheet(text),WEEK).getFirst();
        assertThat(row.periodStart()).isEqualTo(Instant.parse("2026-10-28T13:06:20Z"));
        assertThat(row.periodEnd()).isEqualTo(Instant.parse("2026-11-04T13:06:20Z"));
        assertThat(row.sourceValues()).containsEntry("periodStart","28-10-2026 08:06:20");
    }
}
