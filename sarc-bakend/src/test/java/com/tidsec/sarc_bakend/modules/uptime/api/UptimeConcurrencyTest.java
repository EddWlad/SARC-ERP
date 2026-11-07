package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class UptimeConcurrencyTest extends UptimeApiSupport {

	/** Lanza las dos peticiones a la vez y devuelve sus estados. */
	private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
		CountDownLatch gate = new CountDownLatch(2);
		try (var pool = Executors.newFixedThreadPool(2)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (Callable<Integer> call : List.of(first, second)) {
				results.add(pool.submit(() -> {
					gate.countDown();
					gate.await();
					return call.call();
				}));
			}
			return List.of(results.get(0).get(), results.get(1).get());
		}
	}

	@Test
	void twoCorrectionsOnlyOneWins() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		long version = version(id);
		Callable<Integer> correct = () -> send(analyst, "/measurements/" + id + "/corrections",
				correction(version, "1440", "0")).andReturn().getResponse().getStatus();

		assertThat(race(correct, correct)).containsExactlyInAnyOrder(200, 409);
	}

	@Test
	void twoValidationsOnlyOneWins() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		long version = version(id);
		Callable<Integer> validate = () -> send(analyst, "/measurements/" + id + "/validate",
				"{\"version\":%d}".formatted(version)).andReturn().getResponse().getStatus();

		assertThat(race(validate, validate)).containsExactlyInAnyOrder(200, 409);
	}

	@Test
	void concurrentIdenticalRegistrationKeepsOneMeasurement() throws Exception {
		Callable<Integer> register = () -> send(analyst, "/measurements", measurement(JAC_1, "1401", "39")).andReturn()
				.getResponse().getStatus();

		assertThat(race(register, register)).containsExactlyInAnyOrder(200, 201);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement", Integer.class)).isEqualTo(1);
	}

	@Test
	void batchSkipsObservedStaleAndMissing() throws Exception {
		String ready = registered(JAC_1, "1401", "39").get("id").toString();
		String observed = registered(JAC_2, "1000", "0").get("id").toString();
		UUID missing = UUID.randomUUID();
		String body = """
				{"items":[{"id":"%s","version":%d},{"id":"%s","version":%d},{"id":"%s","version":0}]}
				""".formatted(ready, version(ready), observed, version(observed), missing);

		send(analyst, "/measurements/validation", body).andExpect(status().isOk())
				.andExpect(jsonPath("$.validated").value(1))
				.andExpect(jsonPath("$.skipped[?(@.id=='%s')].reason".formatted(observed)).value("OBSERVED"))
				.andExpect(jsonPath("$.skipped[?(@.id=='%s')].reason".formatted(missing)).value("NOT_FOUND"));

		send(analyst, "/measurements/validation", """
				{"items":[{"id":"%s","version":0}]}
				""".formatted(observed)).andExpect(jsonPath("$.validated").value(0));
	}

	@Test
	void batchReportsStaleAndNotPending() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		send(analyst, "/measurements/validation", """
				{"items":[{"id":"%s","version":99}]}
				""".formatted(id)).andExpect(jsonPath("$.skipped[0].reason").value("STALE_VERSION"));
		validate(id);
		send(analyst, "/measurements/validation", """
				{"items":[{"id":"%s","version":%d}]}
				""".formatted(id, version(id))).andExpect(jsonPath("$.skipped[0].reason").value("NOT_PENDING"));
	}

	@Test
	void batchOverFiveHundredIsRejected() throws Exception {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < 501; i++) {
			items.append(i == 0 ? "" : ",").append("{\"id\":\"").append(UUID.randomUUID()).append("\",\"version\":0}");
		}
		send(analyst, "/measurements/validation", "{\"items\":[" + items + "]}").andExpect(status().is4xxClientError());
	}
    @Test void concurrentReferenceImportsKeepOneCanonicalMeasurementPerConnector() throws Exception {
        org.mockito.Mockito.when(fileStorage.storeDocument(org.mockito.Mockito.anyString(),
            org.mockito.Mockito.anyString(),org.mockito.Mockito.any())).thenReturn("qa006/original");
        jdbc.update("""
            INSERT INTO asset_external_identity(id,equipment_id,platform,scope,external_id,verified,
                verified_at,created_at,updated_at,version)
            VALUES(? ,?,'ENERLINK','Sede QA006','QA005 JAC Estación',true,now(),now(),now(),0)
            """,UUID.randomUUID(),com.tidsec.sarc_bakend.support.Feature005Fixtures.JAC_STATION);
        List<String> ids=new ArrayList<>();
        for(int n=0;n<2;n++){
            String response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/uptime/imports").file(new org.springframework.mock.web.MockMultipartFile(
                    "file","Semana.xlsx","application/octet-stream",
                    com.tidsec.sarc_bakend.support.Feature006Fixtures.weeklyWorkbook()))
                .param("weekStart","2026-10-26").cookie(analyst)
                .with(com.tidsec.sarc_bakend.support.WebSessions.xsrf()))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            ids.add(com.jayway.jsonpath.JsonPath.read(response,"$.id"));
        }
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(()->ids.stream()
            .allMatch(id->"READY".equals(jdbc.queryForObject("SELECT status FROM upt_import_batch WHERE id=?::uuid",String.class,id))));
        Callable<Integer> a=()->send(analyst,"/imports/"+ids.get(0)+"/confirm",
            "{\"version\":%d,\"exactConfirmed\":false}".formatted(jdbc.queryForObject(
                "SELECT version FROM upt_import_batch WHERE id=?::uuid",Long.class,ids.get(0))))
            .andReturn().getResponse().getStatus();
        Callable<Integer> b=()->send(analyst,"/imports/"+ids.get(1)+"/confirm",
            "{\"version\":%d,\"exactConfirmed\":false}".formatted(jdbc.queryForObject(
                "SELECT version FROM upt_import_batch WHERE id=?::uuid",Long.class,ids.get(1))))
            .andReturn().getResponse().getStatus();
        assertThat(race(a,b)).containsOnly(202);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(()->ids.stream()
            .allMatch(id->"COMPLETED".equals(jdbc.queryForObject("SELECT status FROM upt_import_batch WHERE id=?::uuid",String.class,id))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement WHERE period_start IS NULL AND period_precision='REFERENCE_WEEK'",
            Integer.class)).isEqualTo(2);
    }
}
