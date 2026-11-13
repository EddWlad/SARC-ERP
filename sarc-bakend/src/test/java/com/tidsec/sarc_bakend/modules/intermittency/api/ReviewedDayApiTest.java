package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

import jakarta.servlet.http.Cookie;

/** Dias revisados de la red KIA (FR-044, FR-045): marcar un dia terminado, avisar de lo pendiente y retirar la marca. */
class ReviewedDayApiTest extends IntermittencyImportSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guayaquil"));
	private static final LocalDate YESTERDAY = TODAY.minusDays(1);

	private String days(String path) {
		return "/reviewed-days" + path;
	}

	private int events(LocalDate day, String action) {
		return jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day_event WHERE day = ?::date AND action = ?",
				Integer.class, day.toString(), action);
	}

	/**
	 * Estas pruebas usan «ayer» y «hoy» de la zona operativa y el servidor lee el reloj real: a minutos de la
	 * medianoche de Guayaquil el dia podria cambiar a mitad de una prueba, asi que no se ejecutan entonces.
	 */
	@BeforeEach
	void notNearMidnight() {
		LocalTime now = LocalTime.now(ZoneId.of("America/Guayaquil"));
		Assumptions.assumeTrue(now.isAfter(LocalTime.of(0, 10)) && now.isBefore(LocalTime.of(23, 50)),
				"Demasiado cerca de la medianoche de Guayaquil");
	}

	@Test
	void markingYesterdayKeepsWhoAndWhenAndLeavesAnEventAndAnAuditRecord() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk()).andExpect(jsonPath("$.day.day").value(YESTERDAY.toString()))
				.andExpect(jsonPath("$.day.active").value(true)).andExpect(jsonPath("$.day.finished").value(true))
				.andExpect(jsonPath("$.day.version").value(0)).andExpect(jsonPath("$.warnings", hasSize(0)));

		assertThat(jdbc.queryForObject("SELECT active FROM itm_reviewed_day WHERE day = ?::date", Boolean.class,
				YESTERDAY.toString())).isTrue();
		assertThat(events(YESTERDAY, "MARKED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'INTERMITTENCY_DAY_MARKED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void markingTodayOrAFutureDayIsRejected() throws Exception {
		assertProblem(put(analyst(), days("/" + TODAY)), 422, "DAY_NOT_FINISHED");
		assertProblem(put(analyst(), days("/" + TODAY.plusDays(3))), 422, "DAY_NOT_FINISHED");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day", Integer.class)).isZero();
	}

	@Test
	void markingTwiceChangesNothingAndLeavesASingleEvent() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());

		put(as(Role.ADMINISTRADOR), days("/" + YESTERDAY)).andExpect(status().isOk())
				.andExpect(jsonPath("$.day.version").value(0));

		assertThat(events(YESTERDAY, "MARKED")).isEqualTo(1);
	}

	@Test
	void marksTheDayWarningAboutPreviewBatchesThatTouchItWithoutCountingThemAsCoverage() throws Exception {
		String batch = uploadReady(csv(entry(2, YESTERDAY.toString(), Feature008Fixtures.BLOCKED_CODE, "0:30", "08:00",
				"08:30")), "turno.csv");

		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk()).andExpect(jsonPath("$.warnings", hasSize(1)))
				.andExpect(jsonPath("$.warnings[0].batchId").value(batch))
				.andExpect(jsonPath("$.warnings[0].fileName").value("turno.csv"))
				.andExpect(jsonPath("$.day.unconfirmedBatches").value(1));

		assertThat(jdbc.queryForObject("SELECT pending_batches_warned FROM itm_reviewed_day_event WHERE day = ?::date",
				Integer.class, YESTERDAY.toString())).isEqualTo(1);
	}

	@Test
	void withdrawingNeedsAJustificationAndKeepsTheHistory() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());

		assertProblem(post(analyst(), days("/" + YESTERDAY + "/withdraw"), "{\"version\":0}"), 422, "JUSTIFICATION_REQUIRED");
		assertProblem(post(analyst(), days("/" + YESTERDAY + "/withdraw"), "{\"version\":0,\"justification\":\"  \"}"), 422,
				"JUSTIFICATION_REQUIRED");
		post(analyst(), days("/" + YESTERDAY + "/withdraw"), "{\"version\":0,\"justification\":\"Se marco por error\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.version").value(1));

		assertThat(jdbc.queryForObject("SELECT justification FROM itm_reviewed_day_event WHERE action = 'WITHDRAWN'",
				String.class)).isEqualTo("Se marco por error");
		assertThat(events(YESTERDAY, "MARKED")).isEqualTo(1);
		// Ya sin marca no se retira otra vez.
		assertProblem(post(analyst(), days("/" + YESTERDAY + "/withdraw"), "{\"version\":1,\"justification\":\"Otra vez\"}"),
				422, "DAY_NOT_REVIEWED");
	}

	@Test
	void aDayCanBeMarkedAgainAfterWithdrawingItAndTheEventsStayInOrder() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());
		post(analyst(), days("/" + YESTERDAY + "/withdraw"), "{\"version\":0,\"justification\":\"Falto un cargador\"}")
				.andExpect(status().isOk());

		put(as(Role.ADMINISTRADOR), days("/" + YESTERDAY)).andExpect(status().isOk())
				.andExpect(jsonPath("$.day.active").value(true)).andExpect(jsonPath("$.day.version").value(2));

		assertThat(events(YESTERDAY, "MARKED")).isEqualTo(2);
		assertThat(events(YESTERDAY, "WITHDRAWN")).isEqualTo(1);
	}

	@Test
	void withdrawingWithAnOldVersionIsAConcurrentModificationAndAMissingMarkIsNotFound() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());

		assertProblem(post(analyst(), days("/" + YESTERDAY + "/withdraw"), "{\"version\":7,\"justification\":\"Motivo\"}"),
				409, "CONCURRENT_MODIFICATION");
		assertProblem(post(analyst(), days("/" + YESTERDAY.minusDays(5) + "/withdraw"),
				"{\"version\":0,\"justification\":\"Motivo\"}"), 404, "REVIEWED_DAY_NOT_FOUND");

		assertThat(jdbc.queryForObject("SELECT active FROM itm_reviewed_day WHERE day = ?::date", Boolean.class,
				YESTERDAY.toString())).isTrue();
	}

	@Test
	void theListHasOneEntryPerDayOfTheRangeWithOrWithoutAMarkAndCountsWhatBlocksOrWaits() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());
		LocalDate from = TODAY.minusDays(3);

		String body = get(analyst(), days("?from=" + from + "&to=" + TODAY.plusDays(1))).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4))).andReturn().getResponse().getContentAsString();

		List<Boolean> active = JsonPath.read(body, "$[*].active");
		List<Boolean> finished = JsonPath.read(body, "$[*].finished");
		assertThat(active).containsExactly(false, false, true, false);
		assertThat(finished).containsExactly(true, true, true, false);
		assertThat((String) JsonPath.read(body, "$[0].day")).isEqualTo(from.toString());
		// Sin rango, los ultimos 31 dias, hoy incluido.
		get(analyst(), days("")).andExpect(jsonPath("$", hasSize(31)));
		assertProblem(get(analyst(), days("?from=" + TODAY + "&to=" + TODAY)), 422, "PERIOD_INVALID");
		assertProblem(get(analyst(), days("?from=" + TODAY.minusDays(400) + "&to=" + TODAY)), 422, "PERIOD_TOO_LONG");
	}

	@Test
	void aWindowRegisteredAfterMarkingReducesTheAvailabilityWithoutRemovingTheMark() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());
		String range = "?preset=CUSTOM&from=" + YESTERDAY + "&to=" + TODAY;
		get(analyst(), "/availability" + range).andExpect(status().isOk())
				.andExpect(jsonPath("$.aggregate.offlineMinutes").value(0));

		insertWindow(Feature008Fixtures.BLOCKED, at(YESTERDAY.toString(), "08:00"), at(YESTERDAY.toString(), "09:12"),
				"CONFIRMED");

		get(analyst(), "/availability" + range).andExpect(status().isOk())
				.andExpect(jsonPath("$.aggregate.offlineMinutes").value(72))
				.andExpect(jsonPath("$.coverage.coveredDays").value(1));
		assertThat(jdbc.queryForObject("SELECT active FROM itm_reviewed_day WHERE day = ?::date", Boolean.class,
				YESTERDAY.toString())).isTrue();
	}

	@Test
	void readOnlyRolesAndTheirCombinationCannotListMarkOrWithdraw() throws Exception {
		put(analyst(), days("/" + YESTERDAY)).andExpect(status().isOk());
		List<Cookie> readers = List.of(as(Role.GERENTE), as(Role.SUPERVISOR), as(Role.ASISTENTE),
				as(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE));

		for (Cookie reader : readers) {
			assertProblem(get(reader, days("")), 403, "ACCESS_DENIED");
			assertProblem(put(reader, days("/" + YESTERDAY.minusDays(1))), 403, "ACCESS_DENIED");
			assertProblem(post(reader, days("/" + YESTERDAY + "/withdraw"), "{\"version\":0,\"justification\":\"No\"}"),
					403, "ACCESS_DENIED");
		}
		// Un rol de consulta combinado con uno de edicion si puede.
		get(as(Role.ANALISTA, Role.GERENTE), days("")).andExpect(status().isOk());
		get(as(Role.ADMINISTRADOR, Role.ASISTENTE), days("")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day", Integer.class)).isEqualTo(1);
	}

	@Test
	void aDayWithAnInvalidFormatIsAClientErrorNeverAServerError() throws Exception {
		for (String invalid : new String[] { "abc", "2026-13-40", "15-10-2026" }) {
			int status = put(analyst(), days("/" + invalid)).andReturn().getResponse().getStatus();
			assertThat(status).as("PUT con el dia %s", invalid).isBetween(400, 499);
			int withdraw = post(analyst(), days("/" + invalid + "/withdraw"), "{\"version\":0,\"justification\":\"Motivo\"}")
					.andReturn().getResponse().getStatus();
			assertThat(withdraw).as("retiro con el dia %s", invalid).isBetween(400, 499);
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day", Integer.class)).isZero();
	}
}
