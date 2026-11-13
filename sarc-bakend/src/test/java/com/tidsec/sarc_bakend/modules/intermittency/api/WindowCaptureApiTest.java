package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

class WindowCaptureApiTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;

	@Test
	void savesSixWindowsOfOnePointAndDayInASingleRequest() throws Exception {
		ResultActions result = post(analyst(), "/windows", capture(POINT,
				window(at("08:00"), at("08:10")), window(at("09:00"), at("09:10")), window(at("10:00"), at("10:10")),
				window(at("11:00"), at("11:10")), window(at("12:00"), at("12:10")), window(at("13:00"), at("13:10"))))
				.andExpect(status().isCreated());

		result.andExpect(jsonPath("$.windows", hasSize(6)))
				.andExpect(jsonPath("$.windows[0].state").value("CONFIRMED"))
				.andExpect(jsonPath("$.windows[0].durationMinutes").value(10))
				.andExpect(jsonPath("$.windows[0].durationText").value("0:10"))
				.andExpect(jsonPath("$.windows[0].pointLabel").value("QA008 Punto Bloqueado (900001)"))
				.andExpect(jsonPath("$.dayTotals", hasSize(1)))
				.andExpect(jsonPath("$.dayTotals[0].day").value(DAY))
				.andExpect(jsonPath("$.dayTotals[0].minutes").value(60))
				.andExpect(jsonPath("$.dayTotals[0].text").value("1:00"));
		assertThat(count("equipment_id = '" + POINT + "' AND state = 'CONFIRMED'")).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE change = 'CREATED'",
				Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM audit_event WHERE action = 'INTERMITTENCY_WINDOW_CREATED'", Integer.class))
				.isEqualTo(6);
	}

	@Test
	void splitsTheDayTotalOfAWindowThatCrossesMidnight() throws Exception {
		post(analyst(), "/windows", capture(POINT, window(at(DAY, "23:50"), at("2026-10-16", "00:20"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.windows[0].durationMinutes").value(30))
				.andExpect(jsonPath("$.dayTotals", hasSize(2)))
				.andExpect(jsonPath("$.dayTotals[0].day").value("2026-10-15"))
				.andExpect(jsonPath("$.dayTotals[0].minutes").value(10))
				.andExpect(jsonPath("$.dayTotals[1].day").value("2026-10-16"))
				.andExpect(jsonPath("$.dayTotals[1].minutes").value(20));
	}

	@Test
	void anOpenWindowKeepsItsProvisionalDurationApartAndCanBeClosedLater() throws Exception {
		String created = post(analyst(), "/windows", capture(POINT, window(at("16:01"), null)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.windows[0].state").value("OPEN"))
				.andExpect(jsonPath("$.windows[0].endAt").doesNotExist())
				.andExpect(jsonPath("$.windows[0].durationMinutes").doesNotExist())
				.andExpect(jsonPath("$.windows[0].provisionalMinutes").value(notNullValue()))
				.andExpect(jsonPath("$.dayTotals", hasSize(0)))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.windows[0].id");
		long version = ((Number) JsonPath.read(created, "$.windows[0].version")).longValue();

		post(analyst(), "/windows/" + id + "/close", "{\"version\":%d,\"endAt\":\"%s\"}".formatted(version, at("16:46")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("CONFIRMED"))
				.andExpect(jsonPath("$.durationMinutes").value(45))
				.andExpect(jsonPath("$.provisionalMinutes").doesNotExist())
				.andExpect(jsonPath("$.revision").value(2));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision WHERE change = 'CLOSED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void closingAnOpenWindowOfFourHoursOrMoreLeavesItInReview() throws Exception {
		UUID open = insertWindow(POINT, at("08:00"), null, "OPEN");

		post(analyst(), "/windows/" + open + "/close", "{\"version\":0,\"endAt\":\"%s\"}".formatted(at("12:00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("REVIEW"))
				.andExpect(jsonPath("$.durationMinutes").value(240))
				.andExpect(jsonPath("$.reviewReasons[0]").value("LONG_WINDOW"));
	}

	@Test
	void closeRejectsAStaleVersionAnAlreadyClosedWindowAndAnInvalidEnd() throws Exception {
		UUID open = insertWindow(POINT, at("08:00"), null, "OPEN");
		UUID closed = insertWindow(Feature008Fixtures.FREE, at("08:00"), at("09:00"), "CONFIRMED");

		assertProblem(post(analyst(), "/windows/" + open + "/close",
				"{\"version\":5,\"endAt\":\"%s\"}".formatted(at("09:00"))), 409, "CONCURRENT_MODIFICATION");
		assertProblem(post(analyst(), "/windows/" + closed + "/close",
				"{\"version\":0,\"endAt\":\"%s\"}".formatted(at("10:00"))), 422, "WINDOW_NOT_OPEN");
		assertProblem(post(analyst(), "/windows/" + open + "/close",
				"{\"version\":0,\"endAt\":\"%s\"}".formatted(at("07:00"))), 422, "WINDOW_END_BEFORE_START");
		assertProblem(post(analyst(), "/windows/" + UUID.randomUUID() + "/close",
				"{\"version\":0,\"endAt\":\"%s\"}".formatted(at("09:00"))), 404, "WINDOW_NOT_FOUND");
		assertThat(count("id = '" + open + "' AND state = 'OPEN'")).isEqualTo(1);
	}

	@Test
	void rejectsOverlapDuplicateEndBeforeStartAndFutureInstants() throws Exception {
		insertWindow(POINT, at("10:00"), at("11:00"), "CONFIRMED");

		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("10:30"), at("10:45")))), 409,
				"WINDOW_OVERLAP");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("10:00"), at("11:00")))), 409,
				"WINDOW_DUPLICATE");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("14:00"), at("13:00")))), 422,
				"WINDOW_END_BEFORE_START");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("14:00"), at("14:00")))), 422,
				"WINDOW_END_BEFORE_START");
		OffsetDateTime inTwoHours = OffsetDateTime.now(ZoneOffset.ofHours(-5)).plusHours(2)
				.truncatedTo(ChronoUnit.MINUTES);
		assertProblem(post(analyst(), "/windows", capture(POINT,
				window(inTwoHours.minusHours(1).toString(), inTwoHours.toString()))), 422, "WINDOW_FUTURE_INSTANT");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(inTwoHours.toString(), null))), 422,
				"WINDOW_FUTURE_INSTANT");
		assertThat(count("equipment_id = '" + POINT + "'")).isEqualTo(1);
	}

	@Test
	void theCurrentMinuteIsAcceptedAsAnEndInstant() throws Exception {
		OffsetDateTime currentMinute = OffsetDateTime.now(ZoneOffset.ofHours(-5)).truncatedTo(ChronoUnit.MINUTES);

		post(analyst(), "/windows", capture(POINT, window(currentMinute.minusMinutes(30).toString(),
				currentMinute.toString()))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.windows[0].durationMinutes").value(30));
	}

	@Test
	void overlapAppliesToReviewTestAndOpenWindowsButNotToAnnulledOnes() throws Exception {
		insertWindow(POINT, at("02:00"), at("07:00"), "REVIEW");
		insertWindow(POINT, at("08:00"), at("09:00"), "TEST");
		insertWindow(POINT, at("10:00"), at("11:00"), "ANNULLED");

		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("03:00"), at("03:30")))), 409,
				"WINDOW_OVERLAP");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("08:30"), at("08:45")))), 409,
				"WINDOW_OVERLAP");
		// Una anulada no bloquea: se puede registrar encima de ella.
		post(analyst(), "/windows", capture(POINT, window(at("10:15"), at("10:45")))).andExpect(status().isCreated());
	}

	@Test
	void anOpenWindowBlocksLaterWindowsOfItsPointUntilItIsClosed() throws Exception {
		UUID open = insertWindow(POINT, at("16:01"), null, "OPEN");

		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("18:00"), at("18:30")))), 409,
				"WINDOW_OVERLAP");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("2026-10-20", "09:00"),
				at("2026-10-20", "09:10")))), 409, "WINDOW_OVERLAP");
		// Antes de su inicio queda libre, y otro punto no se ve afectado.
		post(analyst(), "/windows", capture(POINT, window(at("09:00"), at("09:30")))).andExpect(status().isCreated());
		post(analyst(), "/windows", capture(Feature008Fixtures.FREE, window(at("18:00"), at("18:30"))))
				.andExpect(status().isCreated());

		// Al cerrarla con un fin anterior, lo posterior se acepta.
		post(analyst(), "/windows/" + open + "/close", "{\"version\":0,\"endAt\":\"%s\"}".formatted(at("16:30")))
				.andExpect(status().isOk());
		post(analyst(), "/windows", capture(POINT, window(at("18:00"), at("18:30")))).andExpect(status().isCreated());
	}

	@Test
	void rejectsOutOfScopeEquipmentAndDeletedPoints() throws Exception {
		assertProblem(post(analyst(), "/windows", capture(Feature008Fixtures.KIA_STATION,
				window(at("08:00"), at("08:10")))), 422, "NOT_KIA_CHARGING_POINT");
		assertProblem(post(analyst(), "/windows", capture(Feature008Fixtures.JAC_POINT,
				window(at("08:00"), at("08:10")))), 422, "NOT_KIA_CHARGING_POINT");
		assertProblem(post(analyst(), "/windows", capture(Feature008Fixtures.DELETED,
				window(at("08:00"), at("08:10")))), 422, "DELETED_EQUIPMENT");
		assertProblem(post(analyst(), "/windows", capture(UUID.randomUUID(), window(at("08:00"), at("08:10")))),
				404, "POINT_NOT_FOUND");
		assertThat(count("TRUE")).isZero();
	}

	@Test
	void isAtomicAndListsTheErrorOfEachWindow() throws Exception {
		insertWindow(POINT, at("10:00"), at("11:00"), "CONFIRMED");

		ResultActions result = post(analyst(), "/windows", capture(POINT, window(at("08:00"), at("08:10")),
				window(at("10:30"), at("10:40")), window(at("12:00"), at("11:00")),
				window(at("08:00"), at("08:10"))));

		assertProblem(result, 409, "WINDOW_OVERLAP");
		String body = result.andReturn().getResponse().getContentAsString();
		List<Integer> indexes = JsonPath.read(body, "$.errors[*].index");
		List<String> codes = JsonPath.read(body, "$.errors[*].code");
		assertThat(indexes).containsExactly(1, 2, 3);
		assertThat(codes).containsExactly("WINDOW_OVERLAP", "WINDOW_END_BEFORE_START", "WINDOW_DUPLICATE");
		// Ninguna se guardo, ni siquiera la primera, que era valida.
		assertThat(count("equipment_id = '" + POINT + "'")).isEqualTo(1);
	}

	@Test
	void windowsOfTheSameRequestCannotOverlapOrRepeat() throws Exception {
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("08:00"), at("09:00")),
				window(at("08:30"), at("09:30")))), 409, "WINDOW_OVERLAP");
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("08:00"), at("09:00")),
				window(at("08:00"), at("09:00")))), 409, "WINDOW_DUPLICATE");
		assertThat(count("TRUE")).isZero();
	}

	@Test
	void anInactivePointAcceptsPastWindows() throws Exception {
		post(analyst(), "/windows", capture(Feature008Fixtures.INACTIVE, window(at("08:00"), at("08:10"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.windows[0].state").value("CONFIRMED"))
				.andExpect(jsonPath("$.windows[0].pointLabel").value("QA008 Punto Inactivo (900004)"));
	}

	@Test
	void rejectsInvalidBodies() throws Exception {
		post(analyst(), "/windows", "{\"equipmentId\":\"%s\",\"windows\":[]}".formatted(POINT))
				.andExpect(status().isBadRequest());
		post(analyst(), "/windows", "{\"windows\":[{\"startAt\":\"%s\"}]}".formatted(at("08:00")))
				.andExpect(status().isBadRequest());
		post(analyst(), "/windows", capture(POINT, "{\"startAt\":\"%s\",\"endAt\":\"%s\",\"comment\":\"%s\"}"
				.formatted(at("08:00"), at("08:10"), "x".repeat(501)))).andExpect(status().isBadRequest());
		assertProblem(post(analyst(), "/windows", capture(POINT, window(at("08:00"),
				"2026-10-15T08:10:30-05:00"))), 422, "WINDOW_INSTANT_NOT_MINUTE");
	}

	@Test
	void pointSearchReturnsOnlyKiaPointsToEveryAuthenticatedRole() throws Exception {
		for (Set<Role> roles : Feature008Fixtures.ROLE_SETS) {
			Cookie session = as(roles.toArray(Role[]::new));
			get(session, "/points?query=punto").andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.assignedCode == '900001')]").exists())
					.andExpect(jsonPath("$[?(@.name == 'QA008 JAC Punto')]").doesNotExist())
					.andExpect(jsonPath("$[?(@.name == 'QA008 Punto Eliminado')]").doesNotExist());
		}
		get(analyst(), "/points?query=electrolinera").andExpect(jsonPath("$", hasSize(0)));
		get(analyst(), "/points?query=900002").andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].usageStatus").value("FREE"));
		get(analyst(), "/points?query=sin codigo").andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].assignedCode").doesNotExist());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/points"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void consultRolesCannotCaptureOrClose() throws Exception {
		UUID open = insertWindow(POINT, at("08:00"), null, "OPEN");
		List<Set<Role>> readOnly = List.of(Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
				Set.of(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE));
		for (Set<Role> roles : readOnly) {
			Cookie session = as(roles.toArray(Role[]::new));
			assertProblem(post(session, "/windows", capture(POINT, window(at("05:00"), at("05:10")))), 403,
					"ACCESS_DENIED");
			assertProblem(post(session, "/windows/" + open + "/close",
					"{\"version\":0,\"endAt\":\"%s\"}".formatted(at("09:00"))), 403, "ACCESS_DENIED");
		}
		assertThat(count("equipment_id = '" + POINT + "'")).isEqualTo(1);
		// Quien combina un rol de consulta con uno de edicion si puede.
		post(as(Role.ANALISTA, Role.GERENTE), "/windows", capture(POINT, window(at("05:00"), at("05:10"))))
				.andExpect(status().isCreated());
		post(as(Role.ADMINISTRADOR, Role.ASISTENTE), "/windows", capture(POINT, window(at("06:00"), at("06:10"))))
				.andExpect(status().isCreated());
	}
}
