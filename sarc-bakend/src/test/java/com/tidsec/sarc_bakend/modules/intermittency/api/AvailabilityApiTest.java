package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/**
 * Disponibilidad estimada de prueba por API (FR-042 a FR-046a): la cobertura son los dias revisados, un dia sin
 * marca nunca es 100 % y lo pendiente, abierto o por revisar deja sin cubrir lo que corresponde (FR-045).
 */
class AvailabilityApiTest extends IntermittencyImportSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guayaquil"));
	private static final LocalDate YESTERDAY = TODAY.minusDays(1);

	/** Los puntos KIA activos que no estan desconectados: el alcance de la sembrada de pruebas. */
	private static final int IN_SCOPE = 5;

	private void mark(LocalDate day) throws Exception {
		put(analyst(), "/reviewed-days/" + day).andExpect(status().isOk());
	}

	private ResultActions availability(LocalDate from, LocalDate to) throws Exception {
		return get(analyst(), "/availability?preset=CUSTOM&from=" + from + "&to=" + to);
	}

	private ResultActions yesterday() throws Exception {
		return availability(YESTERDAY, TODAY);
	}

	private void window(java.util.UUID point, LocalDate day, String start, String end, String state) {
		insertWindow(point, at(day.toString(), start), end == null ? null : at(day.toString(), end), state);
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
	void theSpecificationExampleGivesNinetyFiveTheOthersOneHundredAndTheWeightedAggregate() throws Exception {
		mark(YESTERDAY);
		window(Feature008Fixtures.BLOCKED, YESTERDAY, "08:00", "09:12", "CONFIRMED");

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.points", hasSize(IN_SCOPE)))
				.andExpect(jsonPath("$.points[0].name").value("QA008 Punto Bloqueado"))
				.andExpect(jsonPath("$.points[0].percent").value(95.0))
				.andExpect(jsonPath("$.points[0].offlineMinutes").value(72))
				.andExpect(jsonPath("$.points[0].coveredDays").value(1))
				.andExpect(jsonPath("$.points[1].percent").value(100.0))
				.andExpect(jsonPath("$.aggregate.evaluatedMinutes").value(1_440 * IN_SCOPE))
				.andExpect(jsonPath("$.aggregate.offlineMinutes").value(72))
				.andExpect(jsonPath("$.aggregate.percent").value(99.0))
				.andExpect(jsonPath("$.coverage.coveredDays").value(1))
				.andExpect(jsonPath("$.coverage.daysInScope").value(1))
				.andExpect(jsonPath("$.labels.estimated").value(true)).andExpect(jsonPath("$.labels.test").value(true))
				.andExpect(jsonPath("$.labels.origin").value("Registro manual de intermitencias (Kibana)"))
				.andExpect(jsonPath("$.limitations", hasSize(3)));
	}

	@Test
	void aDayWithoutAMarkIsNoDataAndNeverOneHundredPercent() throws Exception {
		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.aggregate.percent").doesNotExist())
				.andExpect(jsonPath("$.aggregate.evaluatedMinutes").value(0))
				.andExpect(jsonPath("$.coverage.coveredDays").value(0))
				.andExpect(jsonPath("$.coverage.daysInScope").value(1))
				.andExpect(jsonPath("$.points[?(@.percent)]", hasSize(0)))
				.andExpect(jsonPath("$.points", hasSize(IN_SCOPE)));
	}

	@Test
	void todayAndFutureDaysNeitherCoverNorCountInTheScope() throws Exception {
		mark(YESTERDAY);

		availability(YESTERDAY, TODAY.plusDays(3)).andExpect(status().isOk())
				.andExpect(jsonPath("$.coverage.daysInScope").value(1)).andExpect(jsonPath("$.coverage.coveredDays").value(1));
	}

	@Test
	void aPendingLinkRowOfAConfirmedBatchLeavesTheDayWithoutCoverageForEveryPoint() throws Exception {
		String id = uploadReady(csv(entry(2, YESTERDAY.toString(), "999999", "0:30", "08:00", "08:30")), "pendiente.csv");
		decide(id, 2, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());
		confirm(id).andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");
		mark(YESTERDAY);

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.coverage.coveredDays").value(0))
				.andExpect(jsonPath("$.blockedDays", hasSize(1)))
				.andExpect(jsonPath("$.blockedDays[0].day").value(YESTERDAY.toString()))
				.andExpect(jsonPath("$.blockedDays[0].pendingRows").value(1))
				.andExpect(jsonPath("$.aggregate.percent").doesNotExist())
				.andExpect(jsonPath("$.points[?(@.percent)]", hasSize(0)));
	}

	@Test
	void aBatchStillInPreviewDoesNotAffectTheCoverage() throws Exception {
		String id = uploadReady(csv(entry(2, YESTERDAY.toString(), "999999", "0:30", "08:00", "08:30")), "vista.csv");
		decide(id, 2, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());
		mark(YESTERDAY);

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.coverage.coveredDays").value(1))
				.andExpect(jsonPath("$.blockedDays", hasSize(0)))
				.andExpect(jsonPath("$.aggregate.percent").value(100.0));
	}

	@Test
	void anOpenWindowLeavesItsPointWithoutCoverageEveryDayFromItsStartUntilItIsClosed() throws Exception {
		for (int back = 3; back >= 1; back--) {
			mark(TODAY.minusDays(back));
		}
		window(Feature008Fixtures.BLOCKED, TODAY.minusDays(3), "08:00", null, "OPEN");

		availability(TODAY.minusDays(3), TODAY).andExpect(status().isOk())
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Bloqueado')].coveredDays", contains(0)))
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Bloqueado' && @.percent)]", hasSize(0)))
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Libre')].coveredDays", contains(3)))
				.andExpect(jsonPath("$.aggregate.evaluatedMinutes").value(4 * 3 * 1_440))
				.andExpect(jsonPath("$.points[-1:].name", contains("QA008 Punto Bloqueado")));
	}

	@Test
	void aWindowForReviewLeavesOnlyItsPointWithoutCoverageThatDay() throws Exception {
		mark(YESTERDAY);
		window(Feature008Fixtures.BLOCKED, YESTERDAY, "08:00", "13:00", "REVIEW");

		yesterday().andExpect(status().isOk())
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Bloqueado')].coveredDays", contains(0)))
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Libre')].coveredDays", contains(1)))
				.andExpect(jsonPath("$.coverage.coveredDays").value(1));
	}

	@Test
	void testWindowsDoNotDiscountNorBlock() throws Exception {
		mark(YESTERDAY);
		window(Feature008Fixtures.BLOCKED, YESTERDAY, "08:00", "09:00", "TEST");

		yesterday().andExpect(status().isOk())
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Bloqueado')].percent", contains(100.0)))
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Bloqueado')].coveredDays", contains(1)))
				.andExpect(jsonPath("$.aggregate.offlineMinutes").value(0));
	}

	@Test
	void disconnectedPointsAreExcludedAndInactiveOrDeletedOnesOnlyWhenTheyHaveNoWindowsInTheRange() throws Exception {
		mark(YESTERDAY);

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.points", hasSize(IN_SCOPE)))
				.andExpect(jsonPath("$.exclusions[*].reason", containsInAnyOrder("DISCONNECTED", "INACTIVE", "DELETED")));

		window(Feature008Fixtures.INACTIVE, YESTERDAY, "08:00", "08:30", "CONFIRMED");

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.points", hasSize(IN_SCOPE + 1)))
				.andExpect(jsonPath("$.exclusions[*].reason", containsInAnyOrder("DISCONNECTED", "DELETED")));
	}

	@Test
	void theTableGoesFromLowestToHighestAndTheChosenPointsRestrictIt() throws Exception {
		mark(YESTERDAY);
		window(Feature008Fixtures.BLOCKED, YESTERDAY, "08:00", "09:12", "CONFIRMED");
		window(Feature008Fixtures.FREE, YESTERDAY, "10:00", "10:30", "CONFIRMED");

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.points[0].name").value("QA008 Punto Bloqueado"))
				.andExpect(jsonPath("$.points[1].name").value("QA008 Punto Libre"))
				.andExpect(jsonPath("$.points[1].percent").value(97.92));
		get(analyst(), "/availability?preset=CUSTOM&from=" + YESTERDAY + "&to=" + TODAY + "&equipmentIds="
				+ Feature008Fixtures.FREE).andExpect(status().isOk()).andExpect(jsonPath("$.points", hasSize(1)))
				.andExpect(jsonPath("$.aggregate.evaluatedMinutes").value(1_440));
	}

	@Test
	void consultingTheAvailabilityLeavesTheOfficialFiguresAlone() throws Exception {
		mark(YESTERDAY);
		window(Feature008Fixtures.BLOCKED, YESTERDAY, "08:00", "09:12", "CONFIRMED");
		String query = "/summary?preset=CUSTOM&from=" + YESTERDAY + "&to=" + TODAY;
		String before = get(analyst(), query).andReturn().getResponse().getContentAsString()
				.replaceAll("\"(cutOff|generatedAt)\":\"[^\"]*\"", "");

		yesterday().andExpect(status().isOk());
		String after = get(analyst(), query).andReturn().getResponse().getContentAsString()
				.replaceAll("\"(cutOff|generatedAt)\":\"[^\"]*\"", "");

		assertThat(after).isEqualTo(before);
	}

	@Test
	void aPointCreatedAfterTheReviewedDayIsNotEvaluatedThatDayNorCountedAsOneHundredPercent() throws Exception {
		mark(YESTERDAY);
		// El punto libre se dio de alta hoy: ayer todavia no existia.
		jdbc.update("UPDATE asset_equipment SET created_at = now() WHERE id = ?", Feature008Fixtures.FREE);

		yesterday().andExpect(status().isOk()).andExpect(jsonPath("$.points", hasSize(IN_SCOPE)))
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Libre')].coveredDays", contains(0)))
				.andExpect(jsonPath("$.points[?(@.name == 'QA008 Punto Libre' && @.percent)]", hasSize(0)))
				.andExpect(jsonPath("$.points[-1:].name", contains("QA008 Punto Libre")))
				.andExpect(jsonPath("$.aggregate.evaluatedMinutes").value(1_440 * (IN_SCOPE - 1)))
				.andExpect(jsonPath("$.coverage.coveredDays").value(1));
	}
}
