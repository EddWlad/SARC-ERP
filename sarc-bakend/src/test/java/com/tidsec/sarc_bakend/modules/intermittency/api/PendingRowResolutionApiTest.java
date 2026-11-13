package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/** Filas KEEP_PENDING de lotes ya confirmados: se listan, se vinculan, se aplican u omiten despues (FR-028). */
class PendingRowResolutionApiTest extends IntermittencyImportSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;
	private static final String CODE = Feature008Fixtures.BLOCKED_CODE;

	/** Un lote confirmado con tres filas pendientes de vincular y una nueva que se aplica. */
	private String confirmedBatch() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30"),
				entry(3, "2026-10-06", "999999", "0:20", "09:00", "09:20"),
				entry(4, "2026-10-05", CODE, "0:30", "10:00", "10:30"),
				entry(5, "2026-10-07", "999998", "0:10", "11:00", "11:10")), "pendientes.csv");
		for (int row : new int[] { 2, 3, 5 }) {
			decide(id, row, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());
		}
		confirm(id).andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");
		return id;
	}

	private String resolveBody(String batchId, int row, String fields) throws Exception {
		return "{\"version\":%d,%s}".formatted(rowVersion(batchId, row), fields);
	}

	private org.springframework.test.web.servlet.ResultActions resolve(String batchId, int row, String fields)
			throws Exception {
		return post(analyst(), BATCHES + "/" + batchId + "/rows/" + rowId(batchId, row) + "/resolve",
				resolveBody(batchId, row, fields));
	}

	@Test
	void listsOnlyPendingRowsOfConfirmedBatchesAndFiltersThem() throws Exception {
		String confirmed = confirmedBatch();
		// Un lote todavia en revision no aporta filas pendientes.
		String preview = uploadReady(csv(entry(2, "2026-10-09", "999997", "0:30", "08:00", "08:30")), "luego.csv");
		decide(preview, 2, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());

		get(analyst(), "/pending-rows").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].batchId").value(confirmed))
				.andExpect(jsonPath("$.content[0].fileName").value("pendientes.csv"))
				.andExpect(jsonPath("$.content[0].row.classification").value("PENDING_LINK"))
				.andExpect(jsonPath("$.content[0].row.touchedDays[0]").value("2026-10-05"));
		get(analyst(), "/pending-rows?from=2026-10-06&to=2026-10-06").andExpect(jsonPath("$.totalElements").value(1));
		get(analyst(), "/pending-rows?classification=CONFLICT").andExpect(jsonPath("$.totalElements").value(0));
		get(analyst(), "/pending-rows?size=2").andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void linkingAndApplyingCreatesTheWindowsAndRemovesTheRowFromThePendingList() throws Exception {
		String id = confirmedBatch();
		assertThat(windowsOf(id)).isEqualTo(1);

		resolve(id, 2, "\"equipmentId\":\"%s\",\"decision\":\"APPLY\"".formatted(POINT)).andExpect(status().isOk())
				.andExpect(jsonPath("$.row.classification").value("NEW"));

		assertThat(windowsOf(id)).isEqualTo(2);
		assertThat(jdbc.queryForObject("""
				SELECT state || ':' || source || ':' || row_number || ':' || pair_number FROM itm_window
				WHERE batch_id = ?::uuid AND row_number = 2
				""", String.class, id)).isEqualTo("CONFIRMED:CSV:2:1");
		assertThat(jdbc.queryForObject("SELECT resolved_by IS NOT NULL AND resolved_at IS NOT NULL FROM itm_import_row WHERE id = ?::uuid",
				Boolean.class, rowId(id, 2))).isTrue();
		get(analyst(), "/pending-rows").andExpect(jsonPath("$.totalElements").value(2));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'INTERMITTENCY_IMPORT_ROW_RESOLVED'",
				Integer.class)).isEqualTo(1);
		// No se resuelve dos veces.
		assertProblem(resolve(id, 2, "\"decision\":\"APPLY\""), 422, "IMPORT_ROW_NOT_PENDING");
	}

	@Test
	void resolvingValidatesAgainstWhatIsStoredNow() throws Exception {
		String id = confirmedBatch();
		UUID stored = insertWindow(POINT, at("2026-10-06", "09:10"), at("2026-10-06", "09:30"), "CONFIRMED");

		assertProblem(resolve(id, 3, "\"equipmentId\":\"%s\",\"decision\":\"APPLY\"".formatted(POINT)), 422,
				"IMPORT_UNRESOLVED_ROWS");
		assertThat(windowsOf(id)).isEqualTo(1);
		assertThat((Object) get(analyst(), "/pending-rows").andReturn().getResponse().getStatus()).isEqualTo(200);

		resolve(id, 3, ("\"equipmentId\":\"%s\",\"decision\":\"APPLY\",\"windows\":[{\"pair\":1,\"resolution\":\"REPLACE\","
				+ "\"justification\":\"El archivo trae la hora correcta\"}]").formatted(POINT)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject(
				"SELECT justification FROM itm_window_revision WHERE window_id = ?::uuid AND change = 'REPLACED_BY_IMPORT'",
				String.class, stored.toString())).isEqualTo("El archivo trae la hora correcta");
		assertThat(jdbc.queryForObject("SELECT duration_minutes FROM itm_window WHERE id = ?::uuid", Integer.class,
				stored.toString())).isEqualTo(20);
	}

	@Test
	void skippingNeedsAReasonAndCreatesNothing() throws Exception {
		String id = confirmedBatch();

		assertProblem(resolve(id, 5, "\"decision\":\"SKIP\""), 422, "SKIP_REASON_REQUIRED");
		resolve(id, 5, "\"decision\":\"SKIP\",\"skipReason\":\"El punto no existe en Inventario\"")
				.andExpect(status().isOk()).andExpect(jsonPath("$.row.decision").value("SKIP"));

		assertThat(windowsOf(id)).isEqualTo(1);
		get(analyst(), "/pending-rows").andExpect(jsonPath("$.totalElements").value(2));
		assertThat(jdbc.queryForObject("SELECT skip_reason FROM itm_import_row WHERE id = ?::uuid", String.class,
				rowId(id, 5))).isEqualTo("El punto no existe en Inventario");
	}

	@Test
	void rejectsInvalidResolutions() throws Exception {
		String id = confirmedBatch();

		assertProblem(resolve(id, 2, "\"equipmentId\":\"%s\",\"decision\":\"APPLY\"".formatted(Feature008Fixtures.JAC_POINT)),
				422, "NOT_KIA_CHARGING_POINT");
		assertProblem(resolve(id, 2, "\"equipmentId\":\"%s\",\"decision\":\"APPLY\"".formatted(Feature008Fixtures.DELETED)),
				422, "DELETED_EQUIPMENT");
		assertProblem(resolve(id, 2, "\"decision\":\"KEEP_PENDING\""), 422, "KEEP_PENDING_NOT_ALLOWED");
		// Sin punto vinculado no se puede aplicar.
		assertProblem(resolve(id, 2, "\"decision\":\"APPLY\""), 422, "IMPORT_UNRESOLVED_ROWS");
		assertThat(windowsOf(id)).isEqualTo(1);
	}

	@Test
	void aRowOfABatchStillInReviewCannotBeResolvedThisWay() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30")), "revision.csv");
		decide(id, 2, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());

		assertProblem(resolve(id, 2, "\"equipmentId\":\"%s\",\"decision\":\"APPLY\"".formatted(POINT)), 422,
				"IMPORT_NOT_COMPLETED");
	}

	@Test
	void consultRolesCannotListOrResolvePendingRows() throws Exception {
		String id = confirmedBatch();
		List<Set<Role>> readOnly = List.of(Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
				Set.of(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE));
		for (Set<Role> roles : readOnly) {
			Cookie session = as(roles.toArray(Role[]::new));
			assertProblem(get(session, "/pending-rows"), 403, "ACCESS_DENIED");
			assertProblem(post(session, BATCHES + "/" + id + "/rows/" + rowId(id, 2) + "/resolve",
					resolveBody(id, 2, "\"decision\":\"SKIP\",\"skipReason\":\"x\"")), 403, "ACCESS_DENIED");
		}
		assertThat(JsonPath.<Integer>read(get(analyst(), "/pending-rows").andReturn().getResponse().getContentAsString(),
				"$.totalElements")).isEqualTo(3);
	}
}
