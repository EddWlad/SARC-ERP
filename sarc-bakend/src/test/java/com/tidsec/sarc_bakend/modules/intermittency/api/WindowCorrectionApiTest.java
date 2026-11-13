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

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/** Correcciones, confirmacion, prueba y anulacion de ventanas, siempre con justificacion e historial (FR-016, FR-017). */
class WindowCorrectionApiTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;

	private static String justified(long version, String justification) {
		return "{\"version\":%d,\"justification\":\"%s\"}".formatted(version, justification);
	}

	private String state(UUID id) {
		return jdbc.queryForObject("SELECT state FROM itm_window WHERE id = ?::uuid", String.class, id.toString());
	}

	@Test
	void aCorrectionKeepsThePreviousRevisionAndTheCurrentOneWins() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");

		patchAs(analyst(), "/windows/" + id, ("{\"version\":0,\"endAt\":\"%s\",\"comment\":\"Fin corregido\","
				+ "\"justification\":\"El fin era 09:30\"}").formatted(at("09:30")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.durationMinutes").value(90))
				.andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.state").value("CONFIRMED"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE window_id = ?::uuid",
				Integer.class, id.toString())).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT duration_minutes FROM itm_window_revision WHERE window_id = ?::uuid AND revision = 1",
				Integer.class, id.toString())).isEqualTo(60);
		assertThat(jdbc.queryForObject(
				"SELECT justification FROM itm_window_revision WHERE window_id = ?::uuid AND revision = 2",
				String.class, id.toString())).isEqualTo("El fin era 09:30");
		get(analyst(), "/windows/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.revisions", hasSize(2)))
				.andExpect(jsonPath("$.revisions[0].durationMinutes").value(60))
				.andExpect(jsonPath("$.revisions[1].change").value("CORRECTED"))
				.andExpect(jsonPath("$.revisions[1].actorId").exists());
	}

	@Test
	void aCorrectionReachingFourHoursGoesBackToReviewAndCanBeConfirmedByItsAuthor() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");

		patchAs(analyst(), "/windows/" + id, "{\"version\":0,\"endAt\":\"%s\",\"justification\":\"Duro toda la manana\"}"
				.formatted(at("12:00"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("REVIEW"))
				.andExpect(jsonPath("$.reviewReasons[0]").value("LONG_WINDOW"));

		post(analyst(), "/windows/" + id + "/confirm-review", justified(1, "Verificado con el proveedor"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"))
				.andExpect(jsonPath("$.reviewReasons", hasSize(0)));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE change = 'REVIEW_CONFIRMED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void everyChangeNeedsAJustification() throws Exception {
		UUID confirmed = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		UUID review = insertWindow(POINT, at("10:00"), at("15:00"), "REVIEW");

		assertProblem(patchAs(analyst(), "/windows/" + confirmed, "{\"version\":0,\"comment\":\"x\"}"), 422,
				"JUSTIFICATION_REQUIRED");
		assertProblem(post(analyst(), "/windows/" + review + "/confirm-review", justified(0, "  ")), 422,
				"JUSTIFICATION_REQUIRED");
		assertProblem(post(analyst(), "/windows/" + confirmed + "/annul", "{\"version\":0}"), 422,
				"JUSTIFICATION_REQUIRED");
		assertProblem(post(analyst(), "/windows/" + confirmed + "/test", "{\"version\":0,\"justification\":\"\"}"), 422,
				"JUSTIFICATION_REQUIRED");
		assertThat(state(confirmed)).isEqualTo("CONFIRMED");
	}

	@Test
	void annulingNeverDeletesAndFreesTheTimeForANewWindow() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("08:30"), at("08:45")))), 409,
				"WINDOW_OVERLAP");

		post(analyst(), "/windows/" + id + "/annul", justified(0, "Registrada por error")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("ANNULLED"));

		assertThat(count("id = '" + id + "'")).isEqualTo(1);
		post(analyst(), "/windows", capture(POINT, window(at("08:30"), at("08:45")))).andExpect(status().isCreated());
		assertProblem(post(analyst(), "/windows/" + id + "/annul", justified(1, "otra vez")), 422,
				"WINDOW_ALREADY_ANNULLED");
		assertProblem(patchAs(analyst(), "/windows/" + id, "{\"version\":1,\"comment\":\"x\",\"justification\":\"y\"}"),
				422, "WINDOW_NOT_CORRECTABLE");
	}

	@Test
	void aReviewWindowCanBeMarkedTestWithoutConfirmingItAndGoesBackToReview() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("13:00"), "REVIEW");

		post(analyst(), "/windows/" + id + "/test", justified(0, "Prueba del proveedor")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("TEST")).andExpect(jsonPath("$.reviewReasons[0]").value("LONG_WINDOW"));
		post(analyst(), "/windows/" + id + "/untest", justified(1, "Era un corte real")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("REVIEW")).andExpect(jsonPath("$.reviewReasons[0]").value("LONG_WINDOW"));
		assertProblem(post(analyst(), "/windows/" + id + "/untest", justified(2, "otra vez")), 422,
				"WINDOW_NOT_TEST");

		UUID open = insertWindow(Feature008Fixtures.FREE, at("08:00"), null, "OPEN");
		assertProblem(post(analyst(), "/windows/" + open + "/test", justified(0, "no se puede")), 422,
				"WINDOW_NOT_MARKABLE");
	}

	@Test
	void markingAPointOnlyAffectsConfirmedAndReviewWindowsOfTheRangeAndReportsTheOpenOnes() throws Exception {
		insertWindow(POINT, at("2026-10-14", "23:00"), at("2026-10-15", "01:00"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-16", "08:00"), at("2026-10-16", "13:00"), "REVIEW");
		insertWindow(POINT, at("2026-10-17", "08:00"), at("2026-10-17", "09:00"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-20", "08:00"), at("2026-10-20", "09:00"), "CONFIRMED");
		UUID open = insertWindow(POINT, at("2026-10-22", "08:00"), null, "OPEN");
		insertWindow(Feature008Fixtures.FREE, at("2026-10-16", "08:00"), at("2026-10-16", "09:00"), "CONFIRMED");

		post(analyst(), "/points/" + POINT + "/test", "{\"from\":\"2026-10-15\",\"to\":\"2026-10-23\","
				+ "\"justification\":\"El proveedor probo el punto\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.affectedWindows").value(4)).andExpect(jsonPath("$.openWindowsNotMarked").value(1));

		assertThat(count("equipment_id = '" + POINT + "' AND state = 'TEST'")).isEqualTo(4);
		assertThat(state(open)).isEqualTo("OPEN");
		assertThat(count("equipment_id = '" + Feature008Fixtures.FREE + "' AND state = 'CONFIRMED'")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE change = 'MARKED_TEST'",
				Integer.class)).isEqualTo(4);
		// No hay una marca permanente del punto: lo que se registre despues se trata con normalidad.
		post(analyst(), "/windows", capture(POINT, window(at("2026-10-21", "09:00"), at("2026-10-21", "09:30"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.windows[0].state").value("CONFIRMED"));

		post(analyst(), "/points/" + POINT + "/untest", "{\"from\":\"2026-10-15\",\"to\":\"2026-10-23\","
				+ "\"justification\":\"Eran cortes reales\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.affectedWindows").value(4)).andExpect(jsonPath("$.openWindowsNotMarked").value(0));
		assertThat(count("equipment_id = '" + POINT + "' AND state = 'TEST'")).isZero();
		assertThat(count("equipment_id = '" + POINT + "' AND state = 'REVIEW'")).isEqualTo(1);
	}

	@Test
	void aPointRangeNeedsAtLeastADayAndAJustification() throws Exception {
		assertProblem(post(analyst(), "/points/" + POINT + "/test",
				"{\"from\":\"2026-10-15\",\"to\":\"2026-10-15\",\"justification\":\"x\"}"), 422, "PERIOD_INVALID");
		post(analyst(), "/points/" + POINT + "/test", "{\"from\":\"2026-10-15\"}").andExpect(status().isBadRequest());
		assertProblem(post(analyst(), "/points/" + Feature008Fixtures.KIA_STATION + "/test",
				"{\"from\":\"2026-10-15\",\"to\":\"2026-10-16\",\"justification\":\"x\"}"), 422,
				"NOT_KIA_CHARGING_POINT");
	}

	@Test
	void aPointRangeWithNoWindowsStillNeedsAJustificationAndLeavesNoAudit() throws Exception {
		int before = jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action LIKE 'INTERMITTENCY_POINT_%'",
				Integer.class);

		assertProblem(post(analyst(), "/points/" + POINT + "/test", "{\"from\":\"2026-10-15\",\"to\":\"2026-10-16\"}"),
				422, "JUSTIFICATION_REQUIRED");
		assertProblem(post(analyst(), "/points/" + POINT + "/untest",
				"{\"from\":\"2026-10-15\",\"to\":\"2026-10-16\",\"justification\":\"   \"}"), 422,
				"JUSTIFICATION_REQUIRED");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action LIKE 'INTERMITTENCY_POINT_%'",
				Integer.class)).isEqualTo(before);
	}

	@Test
	void aCorrectionThatChangesNothingIsRejectedAndLeavesNoRevision() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");

		assertProblem(patchAs(analyst(), "/windows/" + id, "{\"version\":0,\"justification\":\"Sin cambios\"}"), 422,
				"WINDOW_NOT_CHANGED");
		assertProblem(patchAs(analyst(), "/windows/" + id,
				"{\"version\":0,\"endAt\":\"%s\",\"justification\":\"Mismo fin\"}".formatted(at("09:00"))), 422,
				"WINDOW_NOT_CHANGED");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE window_id = ?::uuid",
				Integer.class, id.toString())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT version FROM itm_window WHERE id = ?::uuid", Long.class,
				id.toString())).isZero();
	}

	@Test
	void aStaleVersionIsRejectedWithAConcurrentModificationConflict() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		post(analyst(), "/windows/" + id + "/test", justified(0, "uno")).andExpect(status().isOk());

		assertProblem(post(analyst(), "/windows/" + id + "/untest", justified(0, "dos")), 409,
				"CONCURRENT_MODIFICATION");
		assertProblem(post(analyst(), "/windows/" + UUID.randomUUID() + "/annul", justified(0, "x")), 404,
				"WINDOW_NOT_FOUND");
	}

	@Test
	void aCorrectionCannotOverlapAnotherWindowOfThePoint() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		insertWindow(POINT, at("09:30"), at("10:00"), "CONFIRMED");

		assertProblem(patchAs(analyst(), "/windows/" + id, "{\"version\":0,\"endAt\":\"%s\",\"justification\":\"x\"}"
				.formatted(at("09:45"))), 409, "WINDOW_OVERLAP");
		assertProblem(patchAs(analyst(), "/windows/" + id, "{\"version\":0,\"endAt\":\"%s\",\"justification\":\"x\"}"
				.formatted(at("07:00"))), 422, "WINDOW_END_BEFORE_START");
		assertThat(jdbc.queryForObject("SELECT duration_minutes FROM itm_window WHERE id = ?::uuid", Integer.class,
				id.toString())).isEqualTo(60);
	}

	@Test
	void aRenamedPointKeepsTheRegisteredLabelAndTheDetailShowsTheCurrentName() throws Exception {
		post(analyst(), "/windows", capture(POINT, window(at("08:00"), at("08:30")))).andExpect(status().isCreated());
		String id = jdbc.queryForObject("SELECT id::text FROM itm_window", String.class);
		jdbc.update("UPDATE asset_equipment SET name = 'Nombre nuevo del punto' WHERE id = ?", POINT);

		get(analyst(), "/windows/" + id).andExpect(status().isOk())
				.andExpect(jsonPath("$.window.pointLabel").value("QA008 Punto Bloqueado (900001)"))
				.andExpect(jsonPath("$.currentPointName").value("Nombre nuevo del punto"));
	}

	@Test
	void consultRolesCannotChangeWindowsOrMarkPoints() throws Exception {
		UUID id = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		List<Set<Role>> readOnly = List.of(Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
				Set.of(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE));
		for (Set<Role> roles : readOnly) {
			Cookie session = as(roles.toArray(Role[]::new));
			assertProblem(patchAs(session, "/windows/" + id, "{\"version\":0,\"comment\":\"x\",\"justification\":\"y\"}"),
					403, "ACCESS_DENIED");
			for (String action : List.of("confirm-review", "test", "untest", "annul")) {
				assertProblem(post(session, "/windows/" + id + "/" + action, justified(0, "x")), 403, "ACCESS_DENIED");
			}
			assertProblem(post(session, "/points/" + POINT + "/test",
					"{\"from\":\"2026-10-15\",\"to\":\"2026-10-16\",\"justification\":\"x\"}"), 403, "ACCESS_DENIED");
			assertProblem(post(session, "/points/" + POINT + "/untest",
					"{\"from\":\"2026-10-15\",\"to\":\"2026-10-16\",\"justification\":\"x\"}"), 403, "ACCESS_DENIED");
		}
		assertThat(state(id)).isEqualTo("CONFIRMED");
		post(as(Role.ANALISTA, Role.GERENTE), "/windows/" + id + "/test", justified(0, "Combinacion con edicion"))
				.andExpect(status().isOk());
	}
}
