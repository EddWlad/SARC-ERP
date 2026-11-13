package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/** Dashboard de intermitencias: cifras por periodo y puntos, proyeccion por rol y ausencia de disponibilidad. */
class IntermittencySummaryApiTest extends IntermittencyImportSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;
	private static final String CODE = Feature008Fixtures.BLOCKED_CODE;
	private static final String OCTOBER = "preset=CUSTOM&from=2026-10-01&to=2026-11-01";

	private void seed() {
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-06", "08:00"), at("2026-10-06", "08:10"), "CONFIRMED");
		insertWindow(Feature008Fixtures.FREE, at("2026-10-05", "09:00"), at("2026-10-05", "09:20"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-07", "08:00"), at("2026-10-07", "13:00"), "REVIEW");
		insertWindow(POINT, at("2026-10-08", "08:00"), at("2026-10-08", "09:00"), "TEST");
		insertWindow(POINT, at("2026-10-30", "08:00"), null, "OPEN");
	}

	@Test
	void everyAuthenticatedRoleGetsTheOfficialFigures() throws Exception {
		seed();

		for (Role role : Role.values()) {
			get(as(role), "/summary?" + OCTOBER).andExpect(status().isOk())
					.andExpect(jsonPath("$.official.minutes").value(60))
					.andExpect(jsonPath("$.official.windows").value(3))
					.andExpect(jsonPath("$.official.affectedPoints").value(2))
					.andExpect(jsonPath("$.official.pointDays").value(3))
					.andExpect(jsonPath("$.outsideOfficial.review").value(1))
					.andExpect(jsonPath("$.outsideOfficial.test").value(1))
					.andExpect(jsonPath("$.outsideOfficial.open.count").value(1))
					.andExpect(jsonPath("$.period.preset").value("CUSTOM"))
					.andExpect(jsonPath("$.period.from").value("2026-10-01"))
					.andExpect(jsonPath("$.period.to").value("2026-11-01"))
					.andExpect(jsonPath("$.period.timeZone").value("America/Guayaquil"));
		}
	}

	@Test
	void onlyEditorsReceiveTheDetailOfWhatIsOutsideTheOfficialFigures() throws Exception {
		seed();

		get(analyst(), "/summary?" + OCTOBER).andExpect(status().isOk())
				.andExpect(jsonPath("$.outsideOfficial.details.windows", hasSize(3)))
				// Lo mas reciente primero: abierta (30 oct), prueba (8 oct), por revisar (7 oct).
				.andExpect(jsonPath("$.outsideOfficial.details.windows[0].state").value("OPEN"))
				.andExpect(jsonPath("$.outsideOfficial.details.windows[2].state").value("REVIEW"))
				.andExpect(jsonPath("$.outsideOfficial.details.truncated").value(false));
		for (Role role : new Role[] { Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE }) {
			get(as(role), "/summary?" + OCTOBER).andExpect(status().isOk())
					.andExpect(jsonPath("$.outsideOfficial.details").doesNotExist())
					.andExpect(jsonPath("$.outsideOfficial.review").value(1));
		}
		get(as(Role.ANALISTA, Role.GERENTE), "/summary?" + OCTOBER)
				.andExpect(jsonPath("$.outsideOfficial.details.windows", hasSize(3)));
	}

	@Test
	void theResponseNeverContainsAvailabilityForAnyRole() throws Exception {
		seed();

		for (Role role : Role.values()) {
			String body = get(as(role), "/summary?" + OCTOBER).andReturn().getResponse().getContentAsString();
			assertThat(body.toLowerCase(Locale.ROOT)).doesNotContain("availab").doesNotContain("disponib");
		}
	}

	@Test
	void theEquipmentFilterNarrowsEveryFigure() throws Exception {
		seed();

		get(analyst(), "/summary?" + OCTOBER + "&equipmentIds=" + Feature008Fixtures.FREE).andExpect(status().isOk())
				.andExpect(jsonPath("$.official.minutes").value(20))
				.andExpect(jsonPath("$.official.affectedPoints").value(1))
				.andExpect(jsonPath("$.outsideOfficial.review").value(0))
				.andExpect(jsonPath("$.outsideOfficial.open.count").value(0))
				.andExpect(jsonPath("$.top", hasSize(1)));
		get(analyst(), "/summary?" + OCTOBER + "&equipmentIds=" + POINT + "&equipmentIds=" + Feature008Fixtures.FREE)
				.andExpect(jsonPath("$.official.minutes").value(60));
	}

	@Test
	void anEmptyPeriodHasCountsButNoFalseSeries() throws Exception {
		insertWindow(POINT, at("2026-10-30", "08:00"), null, "OPEN");

		get(analyst(), "/summary?" + OCTOBER).andExpect(status().isOk())
				.andExpect(jsonPath("$.official.minutes").value(0))
				.andExpect(jsonPath("$.official.windows").value(0))
				.andExpect(jsonPath("$.official.averageMinutes").doesNotExist())
				.andExpect(jsonPath("$.outsideOfficial.open.count").value(1))
				.andExpect(jsonPath("$.daily", hasSize(0))).andExpect(jsonPath("$.top", hasSize(0)))
				.andExpect(jsonPath("$.byDurationBand", hasSize(0)));
	}

	@Test
	void aCorrectionShowsInTheNextQueryWithItsCutOff() throws Exception {
		UUID id = insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.official.minutes").value(30))
				.andExpect(jsonPath("$.generatedAt").exists()).andExpect(jsonPath("$.period.cutOff").exists());
		patchAs(analyst(), "/windows/" + id, "{\"version\":0,\"endAt\":\"%s\",\"justification\":\"Fin real\"}"
				.formatted(at("2026-10-05", "09:00"))).andExpect(status().isOk());

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.official.minutes").value(60))
				.andExpect(jsonPath("$.top[0].minutes").value(60));
	}

	@Test
	void theTopOrdersByTimeAndUsesTheCurrentNameOfThePoint() throws Exception {
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:05"), "CONFIRMED");
		for (int minute = 0; minute < 3; minute++) {
			insertWindow(Feature008Fixtures.FREE, at("2026-10-06", "%02d:00".formatted(8 + minute)),
					at("2026-10-06", "%02d:01".formatted(8 + minute)), "CONFIRMED");
		}
		insertWindow(Feature008Fixtures.FREE, at("2026-10-07", "08:00"), at("2026-10-07", "08:30"), "CONFIRMED");

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.top", hasSize(2)))
				.andExpect(jsonPath("$.top[0].equipmentId").value(Feature008Fixtures.FREE.toString()))
				.andExpect(jsonPath("$.top[0].windows").value(4)).andExpect(jsonPath("$.top[0].days").value(2))
				.andExpect(jsonPath("$.top[0].name").isNotEmpty())
				.andExpect(jsonPath("$.top[1].equipmentId").value(POINT.toString()))
				.andExpect(jsonPath("$.topExtendedByTies").value(false));
	}

	@Test
	void pendingRowsOfConfirmedBatchesAreCountedApartAndFollowThePeriodAndTheFilter() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30"),
				entry(3, "2026-10-06", "999999", "0:20", "09:00", "09:20"),
				entry(4, "2026-10-05", CODE, "0:30", "10:00", "10:30")), "pendientes.csv");
		for (int row : new int[] { 2, 3 }) {
			decide(id, row, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());
		}
		confirm(id).andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.outsideOfficial.pendingLink").value(2))
				.andExpect(jsonPath("$.outsideOfficial.conflict").value(0))
				.andExpect(jsonPath("$.official.minutes").value(30))
				.andExpect(jsonPath("$.outsideOfficial.details.pendingRows", hasSize(2)));
		get(analyst(), "/summary?preset=CUSTOM&from=2026-10-06&to=2026-10-07")
				.andExpect(jsonPath("$.outsideOfficial.pendingLink").value(1));
		// Una fila por vincular no tiene punto: con puntos elegidos no se atribuye a ninguno.
		get(analyst(), "/summary?" + OCTOBER + "&equipmentIds=" + POINT)
				.andExpect(jsonPath("$.outsideOfficial.pendingLink").value(0));
	}

	/** Un lote confirmado con dos filas por vincular (5 y 6 de octubre); devuelve los ids de sus filas. */
	private String confirmedBatchWithTwoPendingRows() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", "999999", "0:30", "08:00", "08:30"),
				entry(3, "2026-10-06", "999999", "0:20", "09:00", "09:20")), "pendientes.csv");
		for (int row : new int[] { 2, 3 }) {
			decide(id, row, "\"decision\":\"KEEP_PENDING\"").andExpect(status().isOk());
		}
		confirm(id).andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");
		return id;
	}

	@Test
	void aPendingRowOnlyCountsWhenOneOfItsDaysFallsInsideThePeriod() throws Exception {
		String id = confirmedBatchWithTwoPendingRows();
		// La fila 2 toca el 30 de septiembre y el 5 de noviembre: ningun dia cae en octubre.
		jdbc.update("UPDATE itm_import_row SET touched_days = ARRAY['2026-09-30','2026-11-05']::date[] WHERE id = ?::uuid",
				rowId(id, 2));

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.outsideOfficial.pendingLink").value(1))
				.andExpect(jsonPath("$.outsideOfficial.details.pendingRows", hasSize(1)))
				.andExpect(jsonPath("$.outsideOfficial.details.pendingRows[0].rowNumber").value(3));
		get(analyst(), "/summary?preset=CUSTOM&from=2026-11-01&to=2026-11-30")
				.andExpect(jsonPath("$.outsideOfficial.pendingLink").value(1))
				.andExpect(jsonPath("$.outsideOfficial.details.pendingRows[0].rowNumber").value(2));
		get(analyst(), "/pending-rows?from=2026-10-01&to=2026-10-31").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void conflictRowsOfAConfirmedBatchFollowThePointFilter() throws Exception {
		String id = confirmedBatchWithTwoPendingRows();
		jdbc.update("UPDATE itm_import_row SET classification = 'CONFLICT', equipment_id = ?::uuid WHERE id = ?::uuid",
				POINT.toString(), rowId(id, 2));

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.outsideOfficial.conflict").value(1))
				.andExpect(jsonPath("$.outsideOfficial.pendingLink").value(1));
		get(analyst(), "/summary?" + OCTOBER + "&equipmentIds=" + POINT)
				.andExpect(jsonPath("$.outsideOfficial.conflict").value(1))
				.andExpect(jsonPath("$.outsideOfficial.pendingLink").value(0))
				.andExpect(jsonPath("$.outsideOfficial.details.pendingRows", hasSize(1)));
		get(analyst(), "/summary?" + OCTOBER + "&equipmentIds=" + Feature008Fixtures.FREE)
				.andExpect(jsonPath("$.outsideOfficial.conflict").value(0))
				.andExpect(jsonPath("$.outsideOfficial.details.pendingRows", hasSize(0)));
	}

	@Test
	void annulledWindowsCountForNothingAndAreNotListed() throws Exception {
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-06", "08:00"), at("2026-10-06", "09:00"), "ANNULLED");

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.official.minutes").value(30))
				.andExpect(jsonPath("$.official.windows").value(1)).andExpect(jsonPath("$.outsideOfficial.review").value(0))
				.andExpect(jsonPath("$.outsideOfficial.details.windows", hasSize(0)));
	}

	@Test
	void theDetailKeepsTheNewestHundredAndSaysItWasCut() throws Exception {
		// 112 ventanas de prueba de un minuto, cuatro por dia durante 28 dias.
		for (int day = 1; day <= 28; day++) {
			for (int hour = 8; hour < 12; hour++) {
				String date = "2026-10-%02d".formatted(day);
				insertWindow(POINT, at(date, "%02d:00".formatted(hour)), at(date, "%02d:01".formatted(hour)), "TEST");
			}
		}

		get(analyst(), "/summary?" + OCTOBER).andExpect(jsonPath("$.outsideOfficial.test").value(112))
				.andExpect(jsonPath("$.outsideOfficial.details.windows", hasSize(100)))
				.andExpect(jsonPath("$.outsideOfficial.details.truncated").value(true))
				.andExpect(jsonPath("$.outsideOfficial.details.windows[0].startAt").value("2026-10-28T16:00:00Z"));
	}

	@Test
	void withoutParametersItIsTheCurrentFortnightAndBadPeriodsAreRejected() throws Exception {
		get(analyst(), "/summary").andExpect(status().isOk()).andExpect(jsonPath("$.period.preset").exists())
				.andExpect(jsonPath("$.period.from").exists());
		get(analyst(), "/summary?preset=LAST_15_DAYS").andExpect(status().isOk())
				.andExpect(jsonPath("$.period.preset").value("LAST_15_DAYS"));
		get(analyst(), "/summary?preset=FIRST_HALF&month=2026-10").andExpect(jsonPath("$.period.from").value("2026-10-01"))
				.andExpect(jsonPath("$.period.to").value("2026-10-16"));
		assertProblem(get(analyst(), "/summary?preset=CUSTOM&from=2025-01-01&to=2026-11-01"), 422, "PERIOD_TOO_LONG");
		assertProblem(get(analyst(), "/summary?preset=CUSTOM&from=2026-10-01"), 422, "PERIOD_INVALID");
		assertProblem(get(analyst(), "/summary?preset=FIRST_HALF&month=octubre"), 422, "PERIOD_INVALID");
		get(analyst(), "/summary?preset=OTRO").andExpect(status().isBadRequest());
	}
}
