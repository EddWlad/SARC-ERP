package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.support.WebSessions;

class UptimeImportApiTest extends UptimeImportSupport {

	private static final String BLOCK = "{\"text\":\"Fuera de línea 39\\nDisponible 1296\\nCon falla 0\\nOcupado 105\"}";

	@Test
	void parsesBlockWithoutWriting() throws Exception {
		send(analyst, "/measurements/parse", BLOCK).andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(1296)).andExpect(jsonPath("$.offline").value(39));

		assertThat(measurements()).isZero();
	}

	@Test
	void parsesTheBlockAsEnerlinkCopiesItWithHoursAndMinutesBeforeEachLabel() throws Exception {
		send(analyst, "/measurements/parse", "{\"text\":\"163 hrs 35 mins - Disponible\\n0 hrs 31 mins - Ocupado\\n"
				+ "0 hrs 0 mins - Con falla\\n3 hrs 54 mins - Fuera de línea\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(9815)).andExpect(jsonPath("$.occupied").value(31))
				.andExpect(jsonPath("$.fault").value(0)).andExpect(jsonPath("$.offline").value(234));

		assertThat(measurements()).isZero();
	}

	@Test
	void ambiguousBlockNamesWhatIsMissing() throws Exception {
		send(analyst, "/measurements/parse", "{\"text\":\"Disponible 1296\"}").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_BLOCK_AMBIGUOUS"))
				.andExpect(jsonPath("$.detected", contains("Disponible")))
				.andExpect(jsonPath("$.missing", contains("Ocupado", "Con falla", "Fuera de línea")));
	}

	@Test
	void consultationRolesCannotParseOrImport() throws Exception {
		send(manager, "/measurements/parse", BLOCK).andExpect(status().isForbidden());
		send(manager, "/imports/paste", paste("ROLLING_24H", row("1", "1440", "0"))).andExpect(status().isForbidden());
	}

	@Test
	void exactWeekCannotBeDeclared() throws Exception {
		send(analyst, "/imports/paste", paste("EXACT_WEEK", row("1", "1440", "0")))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_DECLARED_TYPE_INVALID"));
	}

	@Test
	void previewClassifiesWithoutWriting() throws Exception {
		String id = imported("ROLLING_24H", row("1 - Tipo 2", "1440", "0"), row("2-CCS2", "1000", "0"),
				row("9 raro", "1440", "0"), row(SITE, CHARGER, "1", "mal", TO, "1440", "0"));

		rows(id).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].classification", contains("NEW", "NEW", "PENDING_LINK", "INVALID")))
				.andExpect(jsonPath("$.content[0].periodType").value("ROLLING_24H"))
				.andExpect(jsonPath("$.content[1].observations", contains("SUM_BELOW_DURATION")));
		assertThat(measurements()).isZero();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/uptime/imports/" + id).cookie(analyst)).andExpect(jsonPath("$.newRows").value(2))
				.andExpect(jsonPath("$.observedRows").value(1)).andExpect(jsonPath("$.pendingLinkRows").value(1))
				.andExpect(jsonPath("$.invalidRows").value(1));
	}

	@Test
	void weeklyModelIsImportedFromEverySheetWithTheChosenWeekAndItsUptime() throws Exception {
		String body = mvc.perform(multipart("/api/v1/uptime/imports")
				.file(new MockMultipartFile("file", "Modelo_Informe_UpTime.xlsx",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", model()))
				.param("weekStart", "2026-10-19").cookie(analyst).with(WebSessions.xsrf()))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.origin").value("FILE"))
				.andExpect(jsonPath("$.declaredPeriodType").value("ROLLING_7D"))
				.andExpect(jsonPath("$.referenceWeek").value("2026-10-19"))
                .andExpect(jsonPath("$.temporalPrecision").value("REFERENCE_WEEK"))
                .andExpect(jsonPath("$.declaredPeriodStart").doesNotExist())
				.andExpect(jsonPath("$.declaredPeriodEnd").doesNotExist()).andReturn().getResponse()
				.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		awaitStatus(id, "READY");

		rows(id).andExpect(jsonPath("$.content[*].classification", contains("NEW", "PENDING_LINK")))
				.andExpect(jsonPath("$.content[*].sourceSheet", contains("Uptime Electrolineras", "Uptime Ptos. Carga")))
				.andExpect(jsonPath("$.content[0].sourceRow").value(4))
				.andExpect(jsonPath("$.content[0].uptimeBasis").value("REPORTED_PERCENT"))
				.andExpect(jsonPath("$.content[0].observations").isEmpty())
				.andExpect(jsonPath("$.content[0].offlineMinutes").value(61));
		confirm(id, false);

		Map<String, Object> revision = jdbc.queryForMap("SELECT * FROM upt_measurement_revision");
		assertThat(revision.get("origin")).isEqualTo("FILE");
		assertThat(revision.get("uptime_basis")).isEqualTo("REPORTED_PERCENT");
		assertThat(revision.get("occupied_minutes")).isNull();
		assertThat(revision.get("available_minutes")).isEqualTo(9990);
		assertThat((java.math.BigDecimal) revision.get("reported_percent")).isEqualByComparingTo("99.40");
		assertThat(revision.get("equipment_label")).isEqualTo(CHARGER);
		assertThat(jdbc.queryForObject("SELECT period_start FROM upt_measurement", java.sql.Timestamp.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT period_precision FROM upt_measurement",String.class)).isEqualTo("REFERENCE_WEEK");
        assertThat(jdbc.queryForObject("SELECT reference_week::text FROM upt_measurement",String.class)).isEqualTo("2026-10-19");
        String duplicate=uploadWeekly();
        awaitStatus(duplicate,"READY");
        rows(duplicate).andExpect(jsonPath("$.content[0].classification").value("IDENTICAL"));
        confirm(duplicate,false);
        assertThat(measurements()).isEqualTo(1);

		// Corregir sin Ocupado conserva el uptime que informo el archivo.
		String measurement = jdbc.queryForObject("SELECT id::text FROM upt_measurement", String.class);
		send(analyst, "/measurements/" + measurement + "/corrections", """
				{"version":%d,"periodType":"ROLLING_7D","exactConfirmed":false,"availableMinutes":9980,
				 "faultMinutes":10,"offlineMinutes":61,"justification":"Falla omitida"}
				""".formatted(version(measurement))).andExpect(status().isOk())
				.andExpect(jsonPath("$.uptimeBasis").value("REPORTED_PERCENT"))
				.andExpect(jsonPath("$.uptimePercent").value("99.40"));
	}

	@Test
	void weekMustStartOnMondayAndBeOver() throws Exception {
		Map<String, String> expected = Map.of("2026-10-20", "UPTIME_WEEK_INVALID", "2099-10-19", "UPTIME_WEEK_NOT_CLOSED");
		for (Map.Entry<String, String> week : expected.entrySet()) {
			mvc.perform(multipart("/api/v1/uptime/imports")
					.file(new MockMultipartFile("file", "Modelo.xlsx", "application/octet-stream", model()))
					.param("weekStart", week.getKey()).cookie(analyst).with(WebSessions.xsrf()))
					.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value(week.getValue()));
		}
	}

	/** Como el informe que se envia por correo: dos hojas, periodo viejo en la fila 1, horas. */
	private static byte[] model() throws IOException {
		try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			sheet(book, "Uptime Electrolineras", "Conector 1");
			sheet(book, "Uptime Ptos. Carga", "Conector A - PUESTO 4 - CCS2");
			book.write(out);
			return out.toByteArray();
		}
	}

	private static void sheet(XSSFWorkbook book, String name, String connector) {
		Sheet sheet = book.createSheet(name);
		sheet.createRow(0).createCell(0).setCellValue("Uptime 05-10-2026 00:00:00 11-10-2026 23:59:59");
		String[] header = { "Cargador", "Conector", "Uptime (%)", "Fuera de linea (hrs)", "Disponible (hrs)",
				"En falla (hrs)" };
		Object[] values = { CHARGER, connector, 99.4, 1.01, 166.5, 0 };
		Row head = sheet.createRow(2);
		Row data = sheet.createRow(3);
		for (int index = 0; index < header.length; index++) {
			head.createCell(index).setCellValue(header[index]);
			if (values[index] instanceof Number number) {
				data.createCell(index).setCellValue(number.doubleValue());
			}
			else {
				data.createCell(index).setCellValue((String) values[index]);
			}
		}
	}

	@Test
	void durationOutsideDeclaredWindowStaysObserved() throws Exception {
		String id = imported("ROLLING_7D", row("1", "1440", "0"));

		rows(id).andExpect(jsonPath("$.content[0].observations", contains("WINDOW_DURATION_MISMATCH")));
	}

	@Test
	void futurePeriodIsInvalid() throws Exception {
		String id = imported("ROLLING_24H",
				row(SITE, CHARGER, "1", "30-10-2099 14:00:00", "31-10-2099 14:00:00", "1440", "0"));

		assertThat(classification(id)).isEqualTo("INVALID");
	}

	@Test
	void repeatedConnectorAndPeriodIsInvalidInTheSameBatch() throws Exception {
		String id = imported("ROLLING_24H", row("1", "1440", "0"), row("1 Tipo 2", "1440", "0"));

		rows(id).andExpect(jsonPath("$.content[*].classification", contains("NEW", "INVALID")));
	}

	@Test
	void confirmationCreatesPendingMeasurementWithSourceLabel() throws Exception {
		String id = imported("ROLLING_24H", row("1", "1401", "39"));

		confirm(id, false);

		Map<String, Object> revision = jdbc.queryForMap("SELECT * FROM upt_measurement_revision");
		assertThat(revision.get("origin")).isEqualTo("PASTE");
		assertThat(revision.get("review_state")).isEqualTo("PENDING");
		assertThat(revision.get("equipment_label")).isEqualTo(CHARGER);
		assertThat(revision.get("period_type")).isEqualTo("ROLLING_24H");
		assertThat(revision.get("import_batch_id").toString()).isEqualTo(id);
		rows(id).andExpect(jsonPath("$.content[0].outcome").value("APPLIED"));
	}

	@Test
	void reimportingIdenticalWritesNothing() throws Exception {
		confirm(imported("ROLLING_24H", row("1", "1401", "39")), false);

		String again = imported("ROLLING_24H", row("1", "1401", "39"));
		assertThat(classification(again)).isEqualTo("IDENTICAL");
		confirm(again, false);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_revision", Integer.class)).isEqualTo(1);
		rows(again).andExpect(jsonPath("$.content[0].outcome").value("SKIPPED"));
	}

	@Test
	void conflictNeedsJustificationAndThenCorrects() throws Exception {
		confirm(imported("ROLLING_24H", row("1", "1401", "39")), false);
		String id = imported("ROLLING_24H", row("1", "1400", "40"));
		assertThat(classification(id)).isEqualTo("CONFLICT");
		String decision = "{\"rowIds\":[\"%s\"],\"approved\":true%s}";

		decision(id, decision.formatted(firstRowId(id), "")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_JUSTIFICATION_REQUIRED"));
		decision(id, decision.formatted(firstRowId(id), ",\"justification\":\"Enerlink recalculo\"")).andExpect(status().isOk());
		confirm(id, false);

		assertThat(jdbc.queryForObject("SELECT latest_revision FROM upt_measurement", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT justification FROM upt_measurement_revision WHERE revision = 2",
				String.class)).isEqualTo("Enerlink recalculo");
	}

	@Test
	void conflictWithoutDecisionIsSkipped() throws Exception {
		confirm(imported("ROLLING_24H", row("1", "1401", "39")), false);
		String id = imported("ROLLING_24H", row("1", "1400", "40"));

		confirm(id, false);

		assertThat(jdbc.queryForObject("SELECT latest_revision FROM upt_measurement", Integer.class)).isEqualTo(1);
		rows(id).andExpect(jsonPath("$.content[0].outcome").value("SKIPPED"));
	}

	@Test
	void rowLeftOutIsSkipped() throws Exception {
		String id = imported("ROLLING_24H", row("1", "1401", "39"));
		decision(id, "{\"rowIds\":[\"%s\"],\"approved\":false}".formatted(firstRowId(id))).andExpect(status().isOk());

		confirm(id, false);

		assertThat(measurements()).isZero();
	}

	@Test
	void measurementChangedAfterPreviewIsNotOverwritten() throws Exception {
		String id = imported("ROLLING_24H", row("1 - Tipo 2", "1401", "39"));
		send(analyst, "/measurements", measurement(JAC_1, "1440", "0").replace(START, "2026-10-30T14:00:00-05:00"))
				.andExpect(status().isCreated());

		confirm(id, false);

		rows(id).andExpect(jsonPath("$.content[0].outcome").value("FAILED"))
				.andExpect(jsonPath("$.content[0].issues[0].code").value("UPTIME_ROW_RECLASSIFIED"));
		assertThat(jdbc.queryForObject("SELECT offline_minutes FROM upt_measurement_revision", Integer.class)).isZero();
	}

	@Test
	void exactConfirmationReachesOnlyAlignedCleanRows() throws Exception {
		String monday = "19-10-2026 00:00:00";
		String nextMonday = "26-10-2026 00:00:00";
		String id = imported("ROLLING_7D", row(SITE, CHARGER, "1", monday, nextMonday, "10080", "0"),
				row(SITE, CHARGER, "2", "20-10-2026 00:00:00", "27-10-2026 00:00:00", "10080", "0"));

		confirm(id, true);

		assertThat(jdbc.queryForList("""
				SELECT r.period_type FROM upt_measurement_revision r JOIN upt_measurement m ON m.id = r.measurement_id
				ORDER BY m.period_start""", String.class)).containsExactly("EXACT_WEEK", "ROLLING_7D");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_revision WHERE exact_confirmed_by IS NOT NULL",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void retryContinuesWithoutDuplicating() throws Exception {
		String id = imported("ROLLING_24H", row("1", "1401", "39"), row("2", "1401", "39"));
		confirm(id, false);
		jdbc.update("DELETE FROM upt_measurement_revision WHERE measurement_id = (SELECT applied_measurement_id FROM"
				+ " upt_import_row WHERE batch_id = ?::uuid AND row_number = 3)", id);
		jdbc.update("DELETE FROM upt_measurement WHERE id = (SELECT applied_measurement_id FROM upt_import_row"
				+ " WHERE batch_id = ?::uuid AND row_number = 3)", id);
		jdbc.update("UPDATE upt_import_row SET outcome = NULL, applied_measurement_id = NULL WHERE batch_id = ?::uuid"
				+ " AND row_number = 3", id);
		jdbc.update("UPDATE upt_import_batch SET status = 'FAILED', failure_code = 'IMPORT_INTERRUPTED' WHERE id = ?::uuid", id);

		send(analyst, "/imports/" + id + "/retry", "{\"version\":%d}".formatted(batchVersion(id)))
				.andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");

		assertThat(measurements()).isEqualTo(2);
	}

	@Test
	void discardKeepsMeasurementsUntouched() throws Exception {
		String id = imported("ROLLING_24H", row("1", "1401", "39"));

		send(analyst, "/imports/" + id + "/discard", "{\"version\":%d}".formatted(batchVersion(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCARDED"));

		assertThat(measurements()).isZero();
	}

	@Test
	void oversizedConnectorNumberStaysPendingWithoutFailingOtherRows() throws Exception {
		String id = imported("ROLLING_24H", row("99999999999999999999 - Tipo 2", "1401", "39"),
				row("1 - Tipo 2", "1401", "39"));
		confirm(id, false);
		assertThat(measurements()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_import_row WHERE batch_id = ?::uuid AND outcome = 'PENDING_LINK'",
				Integer.class, id)).isEqualTo(1);
	}
    private String uploadWeekly() throws Exception {
        var response=mvc.perform(multipart("/api/v1/uptime/imports")
            .file(new MockMultipartFile("file","Modelo.xlsx","application/octet-stream",model()))
            .param("weekStart","2026-10-19").cookie(analyst).with(WebSessions.xsrf()))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response,"$.id");
    }
    @Test void referenceCannotBeConfirmedAsExactAndKeepsPreview() throws Exception {
        String id=uploadWeekly();awaitStatus(id,"READY");
        send(analyst,"/imports/"+id+"/confirm",
            "{\"version\":%d,\"exactConfirmed\":true}".formatted(batchVersion(id)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("UPTIME_REAL_RANGE_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM upt_import_batch WHERE id=?::uuid",String.class,id)).isEqualTo("READY");
        assertThat(measurements()).isZero();
    }
}
