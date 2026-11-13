package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Layout;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.TimeCells;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

class IntermittencyImportApiTest extends IntermittencyImportSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;
	private static final String CODE = Feature008Fixtures.BLOCKED_CODE;

	private List<String> windowIssues(String row, int pair) {
		return JsonPath.read(row, "$.interpretation.windows[?(@.pair==" + pair + ")].issues[*].code");
	}

	private List<String> rowIssues(String row) {
		return JsonPath.read(row, "$.interpretation.rowIssues[*].code");
	}

	/** El unico valor que devuelve un filtro de JsonPath (que siempre responde con una lista). */
	private static Object one(String json, String path) {
		List<Object> found = JsonPath.read(json, path);
		assertThat(found).as(path).hasSize(1);
		return found.getFirst();
	}

	private int count(String batch, String name) {
		return ((Number) JsonPath.read(batch, "$.counts." + name)).intValue();
	}

	// ----- Carga y clasificacion -----

	@Test
	void uploadsTheWorkbookClassifiesEveryRowAndKeepsTheOriginalWithItsHash() throws Exception {
		byte[] xlsx = SyntheticOperationalLog.xlsx(SyntheticOperationalLog.allRows(), Layout.WITH_HEADER,
				TimeCells.FRACTION);

		String id = uploadReady(xlsx, "registro.xlsx");

		String batch = batch(id);
		assertThat((String) JsonPath.read(batch, "$.format")).isEqualTo("XLSX");
		assertThat((String) JsonPath.read(batch, "$.checksum")).isEqualTo(ContentFingerprint.sha256(xlsx));
		assertThat(count(batch, "rows")).isEqualTo(14);
		assertThat(count(batch, "newRows")).isEqualTo(2);
		assertThat(count(batch, "review")).isEqualTo(1);
		assertThat(count(batch, "conflict")).isEqualTo(2);
		assertThat(count(batch, "invalid")).isEqualTo(9);
		assertThat((String) JsonPath.read(batch, "$.earliestDay")).isEqualTo("2026-10-01");
		assertThat((String) JsonPath.read(batch, "$.latestDay")).isEqualTo("2026-10-18");
		verify(fileStorage).storeDocument(startsWith("imports/intermittency/"), eq("registro.xlsx"), eq(xlsx));
		assertThat(classification(id, 3)).isEqualTo("CONFLICT");
		assertThat(classification(id, 57)).isEqualTo("INVALID");
		assertThat(classification(id, 99)).isEqualTo("REVIEW");
		assertThat(classification(id, 117)).isEqualTo("INVALID");
		assertThat(classification(id, 228)).isEqualTo("CONFLICT");
		assertThat(classification(id, 260)).isEqualTo("NEW");
		assertThat(classification(id, 262)).isEqualTo("NEW");
		assertThat(classification(id, 263)).isEqualTo("INVALID");
	}

	@Test
	void showsRow3WithItsDuplicateAndBothTotals() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		String row = row(id, 3);

		assertThat(one(row, "$.interpretation.windows[?(@.pair==3)].issues[0].message"))
				.isEqualTo("Duplicada en el lote");
		assertThat(windowIssues(row, 3)).containsExactly("DUPLICATE_IN_BATCH");
		assertThat(windowIssues(row, 4)).containsExactly("DUPLICATE_IN_BATCH");
		assertThat((Integer) JsonPath.read(row, "$.interpretation.computedTotalMinutes")).isEqualTo(83);
		assertThat((Integer) JsonPath.read(row, "$.interpretation.computedWithDuplicatesMinutes")).isEqualTo(109);
		assertThat((Integer) JsonPath.read(row, "$.interpretation.fileTotalMinutes")).isEqualTo(109);
		assertThat(rowIssues(row)).doesNotContain("TOTAL_MISMATCH");
		assertThat(one(row, "$.raw[?(@.key=='start3')].value")).isEqualTo("17:55");
	}

	@Test
	void aPastedBlockGivesTheSameCountsAsTheFile() throws Exception {
		String id = pasteReady(new String(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), '\t',
				Layout.WITH_HEADER), java.nio.charset.StandardCharsets.UTF_8));

		String batch = batch(id);
		assertThat((String) JsonPath.read(batch, "$.format")).isEqualTo("PASTE");
		assertThat(count(batch, "rows")).isEqualTo(14);
		assertThat(count(batch, "invalid")).isEqualTo(9);
		assertThat(count(batch, "conflict")).isEqualTo(2);
	}

	@Test
	void filtersRowsByClassificationAndPaginatesThem() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		get(analyst(), BATCHES + "/" + id + "/rows?classification=INVALID&size=100").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(9)).andExpect(jsonPath("$.content[0].rowNumber").value(57));
		get(analyst(), BATCHES + "/" + id + "/rows?size=5&page=2").andExpect(jsonPath("$.content", hasSize(4)))
				.andExpect(jsonPath("$.totalPages").value(3));
		get(analyst(), BATCHES).andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(id));
		get(analyst(), BATCHES + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
	}

	@Test
	void classifiesThePointOfEachRowWithoutAssigningAnythingArbitrarily() throws Exception {
		String id = uploadReady(csv(
				entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30"),
				entry(3, "2026-10-05", Feature008Fixtures.DELETED_CODE, "0:30", "08:00", "08:30"),
				entry(4, "2026-10-05", "qa008-kia-station", "0:30", "08:00", "08:30"),
				entry(5, "2026-10-05", "qa008-jac-point", "0:30", "08:00", "08:30"),
				entry(6, "2026-10-05", Feature008Fixtures.INACTIVE_CODE, "0:30", "08:00", "08:30"),
				entry(7, "2026-10-05", "   ", "0:30", "08:00", "08:30")), "puntos.csv");

		assertThat(classification(id, 2)).isEqualTo("PENDING_LINK");
		assertThat(rowIssues(row(id, 2))).contains("UNKNOWN_CODE");
		assertThat(rowIssues(row(id, 3))).contains("DELETED_EQUIPMENT");
		assertThat(classification(id, 4)).isEqualTo("OUT_OF_SCOPE");
		assertThat(classification(id, 5)).isEqualTo("OUT_OF_SCOPE");
		assertThat(rowIssues(row(id, 5))).contains("NOT_KIA_CHARGING_POINT");
		assertThat(classification(id, 6)).isEqualTo("NEW");
		assertThat(rowIssues(row(id, 6))).contains("INACTIVE_EQUIPMENT", "NAME_DIFFERS");
		assertThat(rowIssues(row(id, 7))).contains("MISSING_CODE");
		assertThat(row(id, 2)).doesNotContain("\"equipmentId\"");
	}

	// ----- Edicion en la vista previa -----

	@Test
	void editingReinterpretsTheRowKeepsTheOriginalAndUpdatesTheCounts() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		edit(id, 57, "\"edits\":[{\"target\":\"window\",\"pair\":1,\"endsNextDay\":true}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("REVIEW"))
				.andExpect(jsonPath("$.batch.counts.invalid").value(8))
				.andExpect(jsonPath("$.batch.counts.review").value(2));

		String row = row(id, 57);
		assertThat(one(row, "$.raw[?(@.key=='end1')].value")).isEqualTo("07:05");
		assertThat((Integer) JsonPath.read(row, "$.interpretation.windows[0].minutes")).isEqualTo(845);
		assertThat((String) JsonPath.read(row, "$.edits[0].target")).isEqualTo("window");
		assertThat((String) JsonPath.read(row, "$.edits[0].previous")).isEqualTo("false");
		assertThat((String) JsonPath.read(row, "$.edits[0].value")).isEqualTo("true");
		assertThat((String) JsonPath.read(row, "$.edits[0].actor")).isNotBlank();
		assertThat((String) JsonPath.read(row, "$.edits[0].at")).isNotBlank();
		assertThat(rowIssues(row)).contains("TOTAL_MISMATCH");
	}

	@Test
	void aMultiDayFallIsCorrectedWithAFullEndDateNotWithAnAutomaticDay() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-06", CODE, "", "10:03", "09:58")), "caida.csv");
		assertThat(classification(id, 2)).isEqualTo("INVALID");

		edit(id, 2, "\"edits\":[{\"target\":\"window\",\"pair\":1,\"endAt\":\"2026-10-08T09:58:00-05:00\"}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("REVIEW"));

		assertThat((Integer) JsonPath.read(row(id, 2), "$.interpretation.windows[0].minutes"))
				.isEqualTo(2 * 1440 - 5);
		assertThat(windowIssues(row(id, 2), 1)).containsExactly("LONG_WINDOW");
	}

	@Test
	void editingARowReclassifiesTheRowsOfTheSamePointAndDay() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "1:00", "08:00", "09:00"),
				entry(3, "2026-10-05", CODE, "1:00", "08:30", "09:30")), "mismo-dia.csv");
		assertThat(classification(id, 2)).isEqualTo("CONFLICT");
		assertThat(classification(id, 3)).isEqualTo("CONFLICT");
		assertThat(windowIssues(row(id, 2), 1)).containsExactly("OVERLAP_IN_BATCH");

		edit(id, 3, "\"edits\":[{\"target\":\"date\",\"value\":\"2026-10-06\"}]").andExpect(status().isOk())
				.andExpect(jsonPath("$.batch.counts.conflict").value(0))
				.andExpect(jsonPath("$.batch.counts.newRows").value(2));

		assertThat(classification(id, 2)).isEqualTo("NEW");
		assertThat(classification(id, 3)).isEqualTo("NEW");
	}

	@Test
	void editsAddAndRemovePairsAndRejectBadValues() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "", "08:00", "08:30", "09:00", "09:30")),
				"pares.csv");

		edit(id, 2, "\"removePairs\":[2],\"addWindows\":[{\"startAt\":\"2026-10-05T14:00:00-05:00\","
				+ "\"endAt\":\"2026-10-05T14:20:00-05:00\"}]").andExpect(status().isOk());

		List<Integer> pairs = JsonPath.read(row(id, 2), "$.interpretation.windows[*].pair");
		assertThat(pairs).containsExactly(1, 3);
		edit(id, 2, "\"addWindows\":[{\"startAt\":\"ayer\"}]").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IMPORT_EDIT_INVALID"));
		edit(id, 2, "\"edits\":[{\"target\":\"window\",\"pair\":9,\"endsNextDay\":true}]")
				.andExpect(status().isNotFound());
		edit(id, 2, "\"edits\":[]").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IMPORT_EDIT_EMPTY"));
		edit(id, 2, "\"edits\":[{\"target\":\"otra\"}]").andExpect(status().isUnprocessableContent());
	}

	@Test
	void anInstantAfterTheCurrentMinuteIsFlaggedWhenEditing() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "", "08:00", "08:30")), "futuro.csv");

		edit(id, 2, "\"edits\":[{\"target\":\"window\",\"pair\":1,\"endAt\":\"2999-01-01T10:00:00-05:00\"}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("INVALID"));

		assertThat(windowIssues(row(id, 2), 1)).contains("FUTURE_INSTANT");
	}

	@Test
	void linkingARowToAPointIsExplicitAndOnlyAllowedForKiaPoints() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30")), "vinculo.csv");
		String body = "{\"version\":%d,\"equipmentId\":\"%s\"}";
		String path = BATCHES + "/" + id + "/rows/" + rowId(id, 2) + "/link";

		assertProblem(post(analyst(), path, body.formatted(rowVersion(id, 2), Feature008Fixtures.JAC_POINT)), 422,
				"NOT_KIA_CHARGING_POINT");
		assertProblem(post(analyst(), path, body.formatted(rowVersion(id, 2), Feature008Fixtures.DELETED)), 422,
				"DELETED_EQUIPMENT");
		assertProblem(post(analyst(), path, body.formatted(rowVersion(id, 2), UUID.randomUUID())), 404,
				"POINT_NOT_FOUND");

		post(analyst(), path, body.formatted(rowVersion(id, 2), POINT)).andExpect(status().isOk())
				.andExpect(jsonPath("$.row.classification").value("NEW"))
				.andExpect(jsonPath("$.row.equipmentId").value(POINT.toString()))
				.andExpect(jsonPath("$.batch.counts.pendingLink").value(0));
		assertThat(jdbc.queryForObject("SELECT linked_by IS NOT NULL FROM itm_import_row WHERE id = ?::uuid",
				Boolean.class, rowId(id, 2))).isTrue();
	}

	// ----- Decisiones -----

	@Test
	void omittingTheDuplicateRevealsTheTotalDifferenceWhichNeedsAnAcknowledgement() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		decide(id, 3, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":4,\"resolution\":\"OMIT\"}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("REVIEW"));
		String row = row(id, 3);
		assertThat((Integer) JsonPath.read(row, "$.interpretation.computedTotalMinutes")).isEqualTo(83);
		assertThat(rowIssues(row)).contains("TOTAL_MISMATCH");

		decide(id, 3, "\"decision\":\"APPLY\",\"acknowledgeTotal\":true").andExpect(status().isOk())
				.andExpect(jsonPath("$.row.classification").value("NEW"));
		assertThat((Boolean) JsonPath.read(row(id, 3), "$.interpretation.totalAcknowledged")).isTrue();
	}

	@Test
	void mergingTwoOverlappingWindowsOfARowLeavesOne() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		decide(id, 228, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":2,\"resolution\":\"MERGE\"}]")
				.andExpect(status().isOk());

		String row = row(id, 228);
		assertThat(one(row, "$.interpretation.windows[?(@.pair==1)].minutes")).isEqualTo(47);
		assertThat(one(row, "$.interpretation.windows[?(@.pair==2)].mergedIntoPair"))
				.isEqualTo(1);
		assertThat(classification(id, 228)).isEqualTo("REVIEW");
		// Una fila sin solapamientos no se puede fusionar.
		assertProblem(decide(id, 260, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"MERGE\"}]"),
				422, "RESOLUTION_NOT_APPLICABLE");
	}

	@Test
	void anIncompletePairCanBeOpenedButNeverTogetherWithTheTestMark() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		decide(id, 117, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":3,\"resolution\":\"OPEN\"}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("NEW"));
		assertThat(one(row(id, 117), "$.interpretation.windows[?(@.pair==3)].decision"))
				.isEqualTo("OPEN");
		// Marcar como prueba una ventana con OPEN se rechaza...
		assertProblem(edit(id, 117, "\"edits\":[{\"target\":\"test\",\"pair\":3,\"value\":\"true\"}]"), 422,
				"OPEN_TEST_NOT_ALLOWED");
		// ...y retirar OPEN y marcarla como prueba primero rechaza elegir OPEN despues, en cualquier orden.
		decide(id, 117, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":3}]").andExpect(status().isOk());
		edit(id, 117, "\"edits\":[{\"target\":\"test\",\"pair\":3,\"value\":\"true\"}]").andExpect(status().isOk());
		assertProblem(decide(id, 117, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":3,\"resolution\":\"OPEN\"}]"),
				422, "OPEN_TEST_NOT_ALLOWED");
		// Solo un par al que le falta el fin se puede abrir.
		assertProblem(decide(id, 260, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"OPEN\"}]"),
				422, "RESOLUTION_NOT_APPLICABLE");
	}

	@Test
	void skippingNeedsAReasonAndKeepPendingIsOnlyForUnlinkedOrConflictingRows() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30"),
				entry(3, "2026-10-05", CODE, "0:30", "09:00", "08:30"),
				entry(4, "2026-10-05", CODE, "0:30", "10:00", "10:30")), "decisiones.csv");

		assertProblem(decide(id, 3, "\"decision\":\"SKIP\""), 422, "SKIP_REASON_REQUIRED");
		decide(id, 3, "\"decision\":\"SKIP\",\"skipReason\":\"Dato tipeado mal en el Excel\"")
				.andExpect(status().isOk()).andExpect(jsonPath("$.batch.counts.skipped").value(1))
				.andExpect(jsonPath("$.batch.counts.invalid").value(0));
		decide(id, 2, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());
		assertProblem(decide(id, 4, "\"decision\":\"KEEP_PENDING\""), 422, "KEEP_PENDING_NOT_ALLOWED");
		assertThat(jdbc.queryForObject("SELECT skip_reason FROM itm_import_row WHERE id = ?::uuid", String.class,
				rowId(id, 3))).isEqualTo("Dato tipeado mal en el Excel");
	}

	@Test
	void aSkippedRowNoLongerOccupiesTimeInTheBatch() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "1:00", "08:00", "09:00"),
				entry(3, "2026-10-05", CODE, "1:00", "08:30", "09:30")), "omitida.csv");
		assertThat(classification(id, 2)).isEqualTo("CONFLICT");

		decide(id, 3, "\"decision\":\"SKIP\",\"skipReason\":\"Es la misma caida\"").andExpect(status().isOk());

		assertThat(classification(id, 2)).isEqualTo("NEW");
	}

	// ----- Comparacion con lo guardado -----

	@Test
	void comparesWithStoredWindowsAndLetsThePersonKeepOrReplaceThem() throws Exception {
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "09:00"), "CONFIRMED");
		UUID stored = insertWindow(POINT, at("2026-10-06", "08:00"), at("2026-10-06", "09:00"), "CONFIRMED");
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "", "08:00", "09:00"),
				entry(3, "2026-10-06", CODE, "", "08:30", "09:30"), entry(4, "2026-10-05", CODE, "", "09:00", "09:30")),
				"guardadas.csv");

		assertThat(classification(id, 2)).isEqualTo("IDENTICAL");
		assertThat(windowIssues(row(id, 2), 1)).containsExactly("ALREADY_RECORDED");
		assertThat(classification(id, 3)).isEqualTo("CONFLICT");
		assertThat(windowIssues(row(id, 3), 1)).containsExactly("OVERLAPS_EXISTING");
		assertThat((String) JsonPath.read(row(id, 3), "$.interpretation.windows[0].conflictWindowId"))
				.isEqualTo(stored.toString());
		assertThat(classification(id, 4)).isEqualTo("NEW");

		decide(id, 3, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"KEEP_EXISTING\"}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("NEW"));
		assertProblem(decide(id, 3, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"REPLACE\"}]"),
				422, "JUSTIFICATION_REQUIRED");
		decide(id, 3, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"REPLACE\","
				+ "\"justification\":\"El archivo trae la hora correcta\"}]").andExpect(status().isOk())
				.andExpect(jsonPath("$.row.classification").value("CONFLICT"));
		assertProblem(decide(id, 4, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"KEEP_EXISTING\"}]"),
				422, "RESOLUTION_NOT_APPLICABLE");
	}

	@Test
	void anOpenChoiceIsComparedAsStartingAtItsStartAndAnOpenStoredWindowBlocksLaterRows() throws Exception {
		insertWindow(POINT, at("2026-10-07", "17:00"), at("2026-10-07", "18:00"), "CONFIRMED");
		insertWindow(Feature008Fixtures.FREE, at("2026-10-07", "10:00"), null, "OPEN");
		String id = uploadReady(csv(entry(2, "2026-10-07", CODE, "", "16:01", ""),
				entry(3, "2026-10-08", Feature008Fixtures.FREE_CODE, "", "09:00", "09:30")), "abierta.csv");
		assertThat(classification(id, 2)).isEqualTo("INVALID");
		// Una abierta guardada bloquea las ventanas posteriores del mismo punto.
		assertThat(classification(id, 3)).isEqualTo("CONFLICT");
		assertThat(windowIssues(row(id, 3), 1)).containsExactly("OVERLAPS_EXISTING");

		decide(id, 2, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"OPEN\"}]")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.classification").value("CONFLICT"));
		assertThat(windowIssues(row(id, 2), 1)).containsExactly("OVERLAPS_EXISTING");
		// Retirar OPEN devuelve la fila a su estado anterior: no queda un conflicto pegado.
		decide(id, 2, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1}]").andExpect(status().isOk())
				.andExpect(jsonPath("$.row.classification").value("INVALID"));
	}

	@Test
	void anOpenChoiceMakesLaterRowsOfTheBatchConflictAndWithdrawingItFreesThem() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-07", CODE, "", "16:01", ""),
				entry(3, "2026-10-08", CODE, "", "13:00", "13:30")), "lote.csv");
		assertThat(classification(id, 3)).isEqualTo("NEW");

		decide(id, 2, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"OPEN\"}]")
				.andExpect(status().isOk());
		assertThat(classification(id, 2)).isEqualTo("CONFLICT");
		assertThat(classification(id, 3)).isEqualTo("CONFLICT");
		assertThat(windowIssues(row(id, 3), 1)).containsExactly("OVERLAP_IN_BATCH");

		decide(id, 2, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1}]").andExpect(status().isOk());
		assertThat(classification(id, 3)).isEqualTo("NEW");
	}

	// ----- Confirmacion -----

	@Test
	void confirmingCreatesTheWindowsWithTheirOriginAndLeavesTestOnesAsTest() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30"),
				entry(3, "2026-10-05", Feature008Fixtures.FREE_CODE, "0:30", "09:00", "09:10", "10:00", "10:20"),
				entry(4, "2026-10-05", Feature008Fixtures.SECOND_BLOCKED_CODE, "5:00", "11:00", "16:00"),
				entry(5, "2026-10-05", Feature008Fixtures.DISCONNECTED_CODE, "", "16:01", "")),
				"confirmar.csv");
		edit(id, 3, "\"edits\":[{\"target\":\"test\",\"pair\":2,\"value\":\"true\"}]").andExpect(status().isOk());
		decide(id, 5, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"OPEN\"}]")
				.andExpect(status().isOk());

		confirm(id).andExpect(status().isAccepted());
		String done = awaitStatus(id, "COMPLETED");

		assertThat((Integer) JsonPath.read(done, "$.appliedRows")).isEqualTo(4);
		assertThat(windowsOf(id)).isEqualTo(5);
		assertThat(jdbc.queryForList("SELECT state FROM itm_window WHERE batch_id = ?::uuid ORDER BY row_number, pair_number",
				String.class, id)).containsExactly("CONFIRMED", "CONFIRMED", "TEST", "REVIEW", "OPEN");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM itm_window WHERE batch_id = ?::uuid AND source = 'CSV' AND row_number IS NOT NULL
				    AND pair_number IS NOT NULL AND point_label LIKE 'QA008%'
				""", Integer.class, id)).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT review_reasons::text FROM itm_window WHERE batch_id = ?::uuid AND state = 'TEST'",
				String.class, id)).isEqualTo("[]");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE change = 'CREATED'",
				Integer.class)).isEqualTo(5);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM audit_event WHERE action = 'INTERMITTENCY_IMPORT_CONFIRMED'", Integer.class))
				.isEqualTo(1);
		// Ya confirmado: no se puede editar ni confirmar otra vez.
		assertProblem(confirm(id), 422, "IMPORT_INVALID_TRANSITION");
	}

	@Test
	void reimportingTheSameDataCreatesNothingAndNeverAnnulsWhatTheFileOmits() throws Exception {
		byte[] file = csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30"),
				entry(3, "2026-10-05", Feature008Fixtures.FREE_CODE, "0:30", "09:00", "09:10", "10:00", "10:20"));
		String first = uploadReady(file, "uno.csv");
		confirm(first).andExpect(status().isAccepted());
		awaitStatus(first, "COMPLETED");
		UUID extra = insertWindow(Feature008Fixtures.DISCONNECTED, at("2026-10-05", "12:00"), at("2026-10-05", "12:30"),
				"CONFIRMED");
		int before = count("TRUE");

		String second = uploadReady(file, "dos.csv");
		String batch = batch(second);
		assertThat(count(batch, "identical")).isEqualTo(2);
		assertThat(count(batch, "newRows")).isZero();
		confirm(second).andExpect(status().isAccepted());
		awaitStatus(second, "COMPLETED");

		assertThat(count("TRUE")).isEqualTo(before);
		assertThat(windowsOf(second)).isZero();
		assertThat(count("id = '" + extra + "' AND state = 'CONFIRMED'")).isEqualTo(1);
	}

	@Test
	void replacingAStoredWindowCreatesAJustifiedRevisionOnConfirm() throws Exception {
		UUID stored = insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "09:00"), "CONFIRMED");
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "", "08:15", "09:15")), "reemplazo.csv");
		decide(id, 2, "\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"REPLACE\","
				+ "\"justification\":\"La hora del archivo es la correcta\"}]").andExpect(status().isOk());

		confirm(id).andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");

		assertThat(jdbc.queryForObject("SELECT duration_minutes FROM itm_window WHERE id = ?::uuid", Integer.class,
				stored.toString())).isEqualTo(60);
		assertThat(jdbc.queryForObject("SELECT start_at = ?::timestamptz FROM itm_window WHERE id = ?::uuid",
				Boolean.class, at("2026-10-05", "08:15"), stored.toString())).isTrue();
		assertThat(jdbc.queryForObject(
				"SELECT justification FROM itm_window_revision WHERE window_id = ?::uuid AND change = 'REPLACED_BY_IMPORT'",
				String.class, stored.toString())).isEqualTo("La hora del archivo es la correcta");
		assertThat(count("TRUE")).isEqualTo(1);
	}

	@Test
	void rowsWithoutADecisionBlockTheConfirmation() throws Exception {
		String id = uploadReady(SyntheticOperationalLog.csv(SyntheticOperationalLog.allRows(), ',', Layout.WITH_HEADER),
				"registro.csv");

		confirm(id).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IMPORT_UNRESOLVED_ROWS"))
				.andExpect(jsonPath("$.unresolvedRows").value(12))
				.andExpect(jsonPath("$.sampleRows[0]").value(3));
		assertThat((String) JsonPath.read(batch(id), "$.status")).isEqualTo("READY");
		assertThat(count("TRUE")).isZero();
	}

	@Test
	void aConflictThatAppearsAfterThePreviewIsCaughtWhenConfirming() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30")), "luego.csv");
		assertThat(classification(id, 2)).isEqualTo("NEW");
		insertWindow(POINT, at("2026-10-05", "08:10"), at("2026-10-05", "08:20"), "CONFIRMED");

		confirm(id).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IMPORT_UNRESOLVED_ROWS"));

		assertThat((String) JsonPath.read(batch(id), "$.status")).isEqualTo("READY");
		assertThat(count("TRUE")).isEqualTo(1);
	}

	// ----- Ciclo de vida -----

	@Test
	void aFileWithoutTheCodeColumnFailsClearlyAndCanBeDiscarded() throws Exception {
		String id = JsonPath.read(upload(analyst(), SyntheticOperationalLog.xlsxWithoutCodeColumn(
				SyntheticOperationalLog.allRows()), "sin-codigo.xlsx").andExpect(status().isAccepted()).andReturn()
				.getResponse().getContentAsString(), "$.id");
		for (int attempt = 0; attempt < 100 && "PARSING".equals(JsonPath.read(batch(id), "$.status")); attempt++) {
			Thread.sleep(100);
		}

		String failed = batch(id);
		assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
		assertThat((String) JsonPath.read(failed, "$.failureCode")).isEqualTo("IMPORT_HEADER_MISSING");
		assertThat((String) JsonPath.read(failed, "$.failureMessage")).contains("Código cargador");
		assertProblem(post(analyst(), BATCHES + "/" + id + "/retry", "{\"version\":%d}".formatted(batchVersion(id))),
				422, "IMPORT_NOT_RETRYABLE");
		post(analyst(), BATCHES + "/" + id + "/discard", "{\"version\":%d}".formatted(batchVersion(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCARDED"));
	}

	@Test
	void discardingAPreviewLeavesNothingBehind() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30")), "descartar.csv");

		post(analyst(), BATCHES + "/" + id + "/discard", "{\"version\":%d}".formatted(batchVersion(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCARDED"));

		assertProblem(confirm(id), 422, "IMPORT_INVALID_TRANSITION");
		assertThat(count("TRUE")).isZero();
		// Reintentar es cargar de nuevo.
		String again = uploadReady(csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30")), "descartar.csv");
		assertThat(again).isNotEqualTo(id);
	}

	@Test
	void rejectsEmptyAndLegacyFiles() throws Exception {
		assertProblem(upload(analyst(), new byte[0], "vacio.csv"), 422, "IMPORT_FILE_EMPTY");
		byte[] xls = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0 };
		assertProblem(upload(analyst(), xls, "viejo.xls"), 422, "IMPORT_FORMAT_UNSUPPORTED");
		assertProblem(post(analyst(), BATCHES + "/paste", "{\"text\":\"   \"}"), 400, "VALIDATION_FAILED");
	}

	@Test
	void consultRolesCannotUseTheImportEndpoints() throws Exception {
		List<Set<Role>> readOnly = List.of(Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
				Set.of(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE));
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30")), "roles.csv");
		for (Set<Role> roles : readOnly) {
			Cookie session = as(roles.toArray(Role[]::new));
			assertProblem(get(session, BATCHES), 403, "ACCESS_DENIED");
			assertProblem(get(session, BATCHES + "/" + id + "/rows"), 403, "ACCESS_DENIED");
			assertProblem(upload(session, csv(entry(2, "2026-10-05", CODE, "0:30", "08:00", "08:30")), "x.csv"), 403,
					"ACCESS_DENIED");
			assertProblem(post(session, BATCHES + "/paste", "{\"text\":\"x\"}"), 403, "ACCESS_DENIED");
			assertProblem(post(session, BATCHES + "/" + id + "/confirm", "{\"version\":0}"), 403, "ACCESS_DENIED");
		}
		get(as(Role.ANALISTA, Role.GERENTE), BATCHES).andExpect(status().isOk());
	}
}
