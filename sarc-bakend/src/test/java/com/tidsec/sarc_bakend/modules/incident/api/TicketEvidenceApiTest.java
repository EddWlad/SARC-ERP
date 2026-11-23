package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

import jakarta.servlet.http.Cookie;

/** Carga y descarga de evidencias (FR-037) con el almacenamiento externo simulado. */
class TicketEvidenceApiTest extends IncidentApiSupport {

	private static final int MB = 1024 * 1024;
	private static final String SIGNED_URL = "https://files.example.test/signed/evidence?token=short-lived";

	@BeforeEach
	void storage() {
		when(fileStorage.storeDocument(anyString(), anyString(), any(byte[].class))).thenReturn("opaque-ref-1");
		when(fileStorage.documentUrl("opaque-ref-1")).thenReturn(Optional.of(SIGNED_URL));
	}

	private UUID ticket() throws Exception {
		return createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
	}

	private static byte[] pdf(int size) {
		byte[] bytes = new byte[size];
		byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(header, 0, bytes, 0, header.length);
		return bytes;
	}

	private static byte[] png(int size) {
		byte[] bytes = new byte[size];
		byte[] header = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
		System.arraycopy(header, 0, bytes, 0, header.length);
		return bytes;
	}

	private ResultActions upload(Cookie session, UUID id, long version, String filename, byte[] bytes, String purpose)
			throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", bytes);
		MockMultipartFile meta = new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
				"{\"version\":%d,\"purpose\":\"%s\",\"note\":\"Captura del tablero\"}".formatted(version, purpose)
						.getBytes(StandardCharsets.UTF_8));
		return mvc.perform(MockMvcRequestBuilders.multipart(BASE + "/tickets/" + id + "/evidence").file(file).file(meta)
				.cookie(session).with(WebSessions.xsrf()));
	}

	private ResultActions upload(UUID id, String filename, byte[] bytes) throws Exception {
		return upload(analyst(), id, versionOf(id), filename, bytes, "GENERAL");
	}

	@Test
	void aTwoMegabytePdfIsAcceptedAndStoredWithItsFingerprint() throws Exception {
		UUID id = ticket();
		byte[] bytes = pdf(2 * MB);

		upload(id, "informe.pdf", bytes).andExpect(status().isOk())
				.andExpect(jsonPath("$.evidence.length()").value(1))
				.andExpect(jsonPath("$.evidence[0].format").value("PDF"))
				.andExpect(jsonPath("$.evidence[0].sizeBytes").value(2 * MB))
				.andExpect(jsonPath("$.evidence[0].originalName").value("informe.pdf"))
				.andExpect(jsonPath("$.evidence[0].purpose").value("GENERAL"))
				.andExpect(jsonPath("$.evidence[0].annulled").value(false))
				.andExpect(jsonPath("$.evidence[0].sha256").value(ContentFingerprint.sha256(bytes)))
				.andExpect(jsonPath("$.timeline[?(@.type=='EVIDENCE_ADDED')]").isNotEmpty())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("opaque-ref-1"))));

		assertThat(jdbc.queryForObject("SELECT sha256 FROM inc_evidence", String.class))
				.isEqualTo(ContentFingerprint.sha256(bytes));
		assertThat(jdbc.queryForObject("SELECT storage_ref FROM inc_evidence", String.class)).isEqualTo("opaque-ref-1");
	}

	@Test
	void aPngNamedAsPdfIsStoredAsPngAndNothingFromTheClientReachesThePath() throws Exception {
		UUID id = ticket();

		upload(id, "../../etc/foto.pdf", png(1024)).andExpect(status().isOk())
				.andExpect(jsonPath("$.evidence[0].format").value("PNG"))
				.andExpect(jsonPath("$.evidence[0].originalName").value("foto.pdf"));

		ArgumentCaptor<String> folder = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
		verify(fileStorage).storeDocument(folder.capture(), filename.capture(), any(byte[].class));
		UUID evidenceId = jdbc.queryForObject("SELECT id FROM inc_evidence", UUID.class);
		assertThat(folder.getValue()).isEqualTo("incidents/evidence/" + id + "/" + evidenceId);
		assertThat(filename.getValue()).isEqualTo(evidenceId + ".png");
		assertThat(folder.getValue() + filename.getValue()).doesNotContain("..").doesNotContain("foto");
	}

	@Test
	void aFileOverTheLimitIs413AndNothingIsStored() throws Exception {
		UUID id = ticket();
		assertProblem(upload(id, "grande.png", png(11 * MB)), 413, "EVIDENCE_TOO_LARGE");
		verify(fileStorage, never()).storeDocument(anyString(), anyString(), any(byte[].class));
		assertThat(count("inc_evidence")).isZero();
		upload(id, "justo.png", png(10 * MB)).andExpect(status().isOk());
	}

	@Test
	void anExecutableAnEmptyFileAndATextFileAreRejectedByTheirContent() throws Exception {
		UUID id = ticket();
		byte[] executable = new byte[2048];
		executable[0] = 'M';
		executable[1] = 'Z';
		assertProblem(upload(id, "foto.png", executable), 422, "EVIDENCE_FORMAT_NOT_ALLOWED");
		assertProblem(upload(id, "nota.pdf", "texto plano".getBytes(StandardCharsets.UTF_8)), 422,
				"EVIDENCE_FORMAT_NOT_ALLOWED");
		assertProblem(upload(id, "vacio.pdf", new byte[0]), 422, "EVIDENCE_FORMAT_NOT_ALLOWED");
		verify(fileStorage, never()).storeDocument(anyString(), anyString(), any(byte[].class));
		assertThat(count("inc_evidence")).isZero();
	}

	@Test
	void everyRoleGetsA302ToTheSignedUrlAndAnonymousGets401() throws Exception {
		UUID id = ticket();
		upload(id, "informe.pdf", pdf(1024)).andExpect(status().isOk());
		UUID evidenceId = jdbc.queryForObject("SELECT id FROM inc_evidence", UUID.class);
		String path = "/tickets/" + id + "/evidence/" + evidenceId;

		get(analyst(), path).andExpect(status().isFound()).andExpect(header().string("Location", SIGNED_URL))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
		get(admin(), path).andExpect(status().isFound());
		for (Cookie reader : readOnlyRoles()) {
			get(reader, path).andExpect(status().isFound()).andExpect(header().string("Location", SIGNED_URL));
		}
		assertProblem(mvc.perform(MockMvcRequestBuilders.get(BASE + path)), 401, "UNAUTHENTICATED");
		assertProblem(get(analyst(), "/tickets/" + id + "/evidence/" + UUID.randomUUID()), 404, "EVIDENCE_NOT_FOUND");
	}

	@Test
	void anEvidenceOfAnotherTicketIsNotReachableThroughThisOne() throws Exception {
		UUID first = ticket();
		upload(first, "informe.pdf", pdf(1024)).andExpect(status().isOk());
		UUID evidenceId = jdbc.queryForObject("SELECT id FROM inc_evidence", UUID.class);
		UUID second = createEquipmentTicket(analyst(), Feature010Fixtures.JAC_POINT, "DISCONNECTION");
		assertProblem(get(analyst(), "/tickets/" + second + "/evidence/" + evidenceId), 404, "EVIDENCE_NOT_FOUND");
	}

	@Test
	void anEvidenceIsNeverDeletedOnlyItsEventIsAnnulled() throws Exception {
		UUID id = ticket();
		upload(id, "informe.pdf", pdf(1024)).andExpect(status().isOk());
		UUID eventId = jdbc.queryForObject("SELECT id FROM inc_event WHERE type = 'EVIDENCE_ADDED'", UUID.class);

		post(analyst(), "/tickets/" + id + "/events/" + eventId + "/annul",
				"{\"version\":%d,\"reason\":\"Se cargó el archivo equivocado\"}".formatted(versionOf(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.evidence.length()").value(1))
				.andExpect(jsonPath("$.evidence[0].annulled").value(true));
		assertThat(count("inc_evidence")).isEqualTo(1);
		verify(fileStorage, never()).delete(anyString());
		get(analyst(), "/tickets/" + id + "/evidence/" + jdbc.queryForObject("SELECT id FROM inc_evidence", UUID.class))
				.andExpect(status().isFound());
	}

	@Test
	void theReadOnlyRolesCannotUploadAndAStaleVersionIs409() throws Exception {
		UUID id = ticket();
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(upload(reader, id, versionOf(id), "informe.pdf", pdf(1024), "GENERAL"), 403, "ACCESS_DENIED");
		}
		assertProblem(upload(analyst(), id, versionOf(id) + 3, "informe.pdf", pdf(1024), "GENERAL"), 409,
				"TICKET_VERSION_CONFLICT");
		verify(fileStorage, never()).storeDocument(anyString(), anyString(), any(byte[].class));
		assertThat(count("inc_evidence")).isZero();
	}

	@Test
	void aStorageFailureLeavesNoEvidenceAndNoEvent() throws Exception {
		UUID id = ticket();
		int events = count("inc_event");
		when(fileStorage.storeDocument(anyString(), anyString(), any(byte[].class)))
				.thenThrow(new ServiceUnavailableException("STORAGE_NOT_CONFIGURED", "Sin almacenamiento"));
		assertProblem(upload(id, "informe.pdf", pdf(1024)), 503, "STORAGE_NOT_CONFIGURED");
		assertThat(count("inc_evidence")).isZero();
		assertThat(count("inc_event")).isEqualTo(events);
	}

	@Test
	void anUnknownRelatedEventIsRejectedBeforeAnythingIsStored() throws Exception {
		UUID id = ticket();
		UUID foreign = UUID.randomUUID();
		MockMultipartFile file = new MockMultipartFile("file", "informe.pdf", "application/pdf", pdf(1024));
		MockMultipartFile meta = new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
				("{\"version\":%d,\"purpose\":\"GENERAL\",\"eventId\":\"%s\"}".formatted(versionOf(id), foreign))
						.getBytes(StandardCharsets.UTF_8));
		assertProblem(mvc.perform(MockMvcRequestBuilders.multipart(BASE + "/tickets/" + id + "/evidence").file(file)
				.file(meta).cookie(analyst()).with(WebSessions.xsrf())), 422, "TICKET_EVENT_INVALID");
		verify(fileStorage, never()).storeDocument(anyString(), anyString(), any(byte[].class));
		assertThat(count("inc_evidence")).isZero();
	}

	@Test
	void ifTheCommitFailsAfterStoringTheFileItIsDeletedFromTheStorage() throws Exception {
		UUID id = ticket();
		// La fila se vuelca al confirmar, ya fuera del metodo: una restriccion que la rechaza simula ese fallo del commit.
		jdbc.execute("ALTER TABLE inc_evidence ADD CONSTRAINT ck_qa_commit_fails CHECK (size_bytes < 0) NOT VALID");
		try {
			int status = upload(id, "informe.pdf", pdf(1024)).andReturn().getResponse().getStatus();
			assertThat(status).isGreaterThanOrEqualTo(400);
			assertThat(count("inc_evidence")).isZero();
			verify(fileStorage).delete(anyString());
		}
		finally {
			jdbc.execute("ALTER TABLE inc_evidence DROP CONSTRAINT ck_qa_commit_fails");
		}
	}
}
