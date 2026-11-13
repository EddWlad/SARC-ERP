package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Entry;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Layout;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Pair;
import com.tidsec.sarc_bakend.support.WebSessions;

/** Ayudas de las pruebas de importacion: cargar, esperar la vista previa, leer filas y corregirlas. */
abstract class IntermittencyImportSupport extends IntermittencyApiSupport {

	static final String BATCHES = "/import-batches";

	@BeforeEach
	void storeOriginals() {
		when(fileStorage.storeDocument(anyString(), anyString(), any())).thenReturn("imports/intermittency/ref");
	}

	/** Una fila del registro con codigo, total y pares {@code (inicio, fin, ...)} en formato {@code H:mm}. */
	static Entry entry(int number, String day, String code, String total, String... times) {
		List<Pair> pairs = new java.util.ArrayList<>();
		for (int i = 0; i + 1 < times.length; i += 2) {
			pairs.add(Pair.of(times[i], times[i + 1]));
		}
		return new Entry(number, java.time.LocalDate.parse(day), null, code,
				com.tidsec.sarc_bakend.support.Feature008Fixtures.POINT_NAMES.getOrDefault(code, "Punto"),
				"Intermitencia", pairs, total);
	}

	static byte[] csv(Entry... rows) {
		return SyntheticOperationalLog.csv(List.of(rows), ',', Layout.WITH_HEADER);
	}

	ResultActions upload(Cookie session, byte[] content, String name) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.multipart(BASE + BATCHES)
				.file(new MockMultipartFile("file", name, "application/octet-stream", content)).cookie(session)
				.with(WebSessions.xsrf()));
	}

	/** Carga como analista, espera la vista previa y devuelve el id del lote. */
	String uploadReady(byte[] content, String name) throws Exception {
		String id = JsonPath.read(upload(analyst(), content, name).andExpect(status().isAccepted()).andReturn()
				.getResponse().getContentAsString(), "$.id");
		awaitStatus(id, "READY");
		return id;
	}

	String pasteReady(String text) throws Exception {
		String id = JsonPath.read(post(analyst(), BATCHES + "/paste", "{\"text\":" + json(text) + "}")
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
		awaitStatus(id, "READY");
		return id;
	}

	/** Espera a que el trabajo en segundo plano deje el lote en ese estado, o falla con el estado final. */
	String awaitStatus(String id, String expected) throws Exception {
		String body = "";
		for (int attempt = 0; attempt < 200; attempt++) {
			body = get(analyst(), BATCHES + "/" + id).andReturn().getResponse().getContentAsString();
			String status = JsonPath.read(body, "$.status");
			if (status.equals(expected)) {
				return body;
			}
			if (!status.equals("PARSING") && !status.equals("CONFIRMING")) {
				throw new AssertionError("El lote quedo en " + status + " en lugar de " + expected + ": " + body);
			}
			Thread.sleep(100);
		}
		throw new AssertionError("El lote no llego a " + expected + ": " + body);
	}

	String batch(String id) throws Exception {
		return get(analyst(), BATCHES + "/" + id).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	long batchVersion(String id) throws Exception {
		return ((Number) JsonPath.read(batch(id), "$.version")).longValue();
	}

	/** La fila del archivo con ese numero, tal como la ve la vista previa. */
	String row(String batchId, int rowNumber) throws Exception {
		String page = get(analyst(), BATCHES + "/" + batchId + "/rows?size=100").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> found = JsonPath.read(page, "$.content[?(@.rowNumber == " + rowNumber + ")]");
		if (found.isEmpty()) {
			throw new AssertionError("No hay una fila " + rowNumber + " en el lote");
		}
		return JsonPath.parse(found.getFirst()).jsonString();
	}

	String rowId(String batchId, int rowNumber) throws Exception {
		return JsonPath.read(row(batchId, rowNumber), "$.id");
	}

	long rowVersion(String batchId, int rowNumber) throws Exception {
		return ((Number) JsonPath.read(row(batchId, rowNumber), "$.version")).longValue();
	}

	String classification(String batchId, int rowNumber) throws Exception {
		return JsonPath.read(row(batchId, rowNumber), "$.classification");
	}

	ResultActions patch(String batchId, int rowNumber, String body) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.patch(BASE + BATCHES + "/" + batchId + "/rows/"
				+ rowId(batchId, rowNumber)).cookie(analyst()).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Edita la fila con su version actual: {@code edits} es el cuerpo JSON sin la version. */
	ResultActions edit(String batchId, int rowNumber, String fields) throws Exception {
		return patch(batchId, rowNumber, "{\"version\":%d,%s}".formatted(rowVersion(batchId, rowNumber), fields));
	}

	/** Decide la fila con su version actual; {@code fields} son los campos restantes del cuerpo. */
	ResultActions decide(String batchId, int rowNumber, String fields) throws Exception {
		return post(analyst(), BATCHES + "/" + batchId + "/rows/" + rowId(batchId, rowNumber) + "/decision",
				"{\"version\":%d,%s}".formatted(rowVersion(batchId, rowNumber), fields));
	}

	ResultActions confirm(String batchId) throws Exception {
		return post(analyst(), BATCHES + "/" + batchId + "/confirm",
				"{\"version\":%d}".formatted(batchVersion(batchId)));
	}

	static String json(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
				+ "\"";
	}

	/** Ventanas no anuladas del lote, para comprobar lo que dejo la confirmacion. */
	int windowsOf(String batchId) {
		return jdbc.queryForObject("SELECT count(*) FROM itm_window WHERE batch_id = ?::uuid", Integer.class, batchId);
	}

	static UUID uuid(String text) {
		return UUID.fromString(text);
	}
}
