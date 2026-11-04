package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.support.Feature005Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

/** Importacion por pegado con encabezados sinteticos: sin valor de evidencia sobre Enerlink. */
abstract class UptimeImportSupport extends UptimeApiSupport {

	static final String HEADER = "ACTIVO\tCARGADOR\tCONECTOR\tDESDE\tHASTA\tDISPONIBLE\tOCUPADO\tCON FALLA\tFUERA DE LINEA\tUPTIME";
	static final String SITE = "Sede JAC";
	static final String CHARGER = "Cargador 1";
	static final String FROM = "30-10-2026 14:00:00";
	static final String TO = "31-10-2026 14:00:00";

	@BeforeEach
	void prepareImport() {
		Mockito.when(fileStorage.storeDocument(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
				.thenReturn("privado/original");
		identity(Feature005Fixtures.JAC_STATION, SITE, CHARGER, true);
	}

	void identity(UUID equipment, String scope, String externalId, boolean verified) {
		jdbc.update("""
				INSERT INTO asset_external_identity (id, equipment_id, platform, scope, external_id, verified,
				    verified_by, verified_at, created_at, updated_at, version)
				VALUES (?, ?, 'ENERLINK', ?, ?, ?, CASE WHEN ? THEN (SELECT id FROM auth_user LIMIT 1) END,
				    CASE WHEN ? THEN now() END, now(), now(), 0)
				""", UUID.randomUUID(), equipment, scope, externalId, verified, verified, verified);
	}

	static String row(String connector, String available, String offline) {
		return row(SITE, CHARGER, connector, FROM, TO, available, offline);
	}

	static String row(String site, String charger, String connector, String from, String to, String available,
			String offline) {
		return String.join("\t", site, charger, connector, from, to, available, "0", "0", offline, "");
	}

	static String paste(String type, String... rows) {
		String text = (HEADER + "\n" + String.join("\n", rows)).replace("\t", "\\t").replace("\n", "\\n");
		return "{\"declaredPeriodType\":\"%s\",\"text\":\"%s\"}".formatted(type, text);
	}

	/** Pega y espera a la vista previa. */
	String imported(String type, String... rows) throws Exception {
		String body = send(analyst, "/imports/paste", paste(type, rows)).andExpect(status().isAccepted()).andReturn()
				.getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		awaitStatus(id, "READY");
		return id;
	}

	void awaitStatus(String id, String expected) {
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> expected
				.equals(jdbc.queryForObject("SELECT status FROM upt_import_batch WHERE id = ?::uuid", String.class, id)));
	}

	long batchVersion(String id) {
		return jdbc.queryForObject("SELECT version FROM upt_import_batch WHERE id = ?::uuid", Long.class, id);
	}

	void confirm(String id, boolean exact) throws Exception {
		send(analyst, "/imports/" + id + "/confirm",
				"{\"version\":%d,\"exactConfirmed\":%s}".formatted(batchVersion(id), exact))
				.andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");
	}

	ResultActions decision(String id, String json) throws Exception {
		return mvc.perform(put("/api/v1/uptime/imports/" + id + "/rows/decision").cookie(analyst)
				.with(WebSessions.xsrf()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions rows(String id) throws Exception {
		return mvc.perform(get("/api/v1/uptime/imports/" + id + "/rows").cookie(analyst));
	}

	String firstRowId(String id) {
		return jdbc.queryForObject("SELECT id::text FROM upt_import_row WHERE batch_id = ?::uuid ORDER BY row_number LIMIT 1",
				String.class, id);
	}

	String classification(String id) {
		List<String> found = jdbc.queryForList(
				"SELECT classification FROM upt_import_row WHERE batch_id = ?::uuid ORDER BY row_number", String.class, id);
		return found.getFirst();
	}

	UUID connectorOf(String id) {
		return jdbc.queryForObject("SELECT connector_id FROM upt_import_row WHERE batch_id = ?::uuid ORDER BY row_number LIMIT 1",
				UUID.class, id);
	}

	int measurements() {
		return jdbc.queryForObject("SELECT count(*) FROM upt_measurement", Integer.class);
	}
}
