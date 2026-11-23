package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

import jakarta.servlet.http.Cookie;

/** Registro del envio real (FR-058, FR-059): lo confirma el analista, aparte de copiar el mensaje. */
class TicketCommunicationApiTest extends IncidentApiSupport {

	@BeforeEach
	void storage() {
		when(fileStorage.storeDocument(anyString(), anyString(), any(byte[].class))).thenReturn("opaque-ref");
	}

	private UUID ticket() throws Exception {
		return createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
	}

	private static String ago(Duration duration) {
		return Instant.now().minus(duration).toString();
	}

	private ResultActions communicate(Cookie session, UUID id, String fields) throws Exception {
		return post(session, "/tickets/" + id + "/communications", "{\"version\":%d,%s}".formatted(versionOf(id), fields));
	}

	private String body(String channel, String kind, String sentAt, String extra) {
		return "\"channel\":\"%s\",\"kind\":\"%s\",\"sentAt\":\"%s\"%s".formatted(channel, kind, sentAt, extra);
	}

	@Test
	void recordsTheRealSendWithChannelKindAndTimeAndLeavesItsEvent() throws Exception {
		UUID id = ticket();
		communicate(analyst(), id, body("KIA_ELECTROLINERAS", "INITIAL", Instant.now().toString(), "")).andExpect(status().isOk())
				.andExpect(jsonPath("$.communications.length()").value(1))
				.andExpect(jsonPath("$.communications[0].channel").value("KIA_ELECTROLINERAS"))
				.andExpect(jsonPath("$.communications[0].kind").value("INITIAL"))
				.andExpect(jsonPath("$.communications[0].retroactive").value(false))
				.andExpect(jsonPath("$.communications[0].annulled").value(false))
				.andExpect(jsonPath("$.communications[0].recordedByName").value("Prueba ANALISTA"))
				.andExpect(jsonPath("$.timeline[?(@.type=='COMMUNICATION_RECORDED')]").isNotEmpty());
		assertThat(count("inc_communication")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT notifiable FROM inc_event WHERE type = 'COMMUNICATION_RECORDED'", Boolean.class))
				.isFalse();
	}

	@Test
	void otherNeedsItsDetail() throws Exception {
		UUID id = ticket();
		assertProblem(communicate(analyst(), id, body("OTHER", "UPDATE", Instant.now().toString(), "")), 422,
				"TICKET_EVENT_INVALID");
		assertProblem(communicate(analyst(), id, body("OTHER", "UPDATE", Instant.now().toString(), ",\"channelDetail\":\" \"")),
				422, "TICKET_EVENT_INVALID");
		communicate(analyst(), id, body("OTHER", "UPDATE", Instant.now().toString(), ",\"channelDetail\":\"Llamada al gerente\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.communications[0].channelDetail").value("Llamada al gerente"));
		assertThat(count("inc_communication")).isEqualTo(1);
	}

	@Test
	void aDetailOfAnotherChannelIsIgnored() throws Exception {
		UUID id = ticket();
		communicate(analyst(), id, body("EMAIL", "UPDATE", Instant.now().toString(), ",\"channelDetail\":\"no cuenta\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.communications[0].channelDetail").doesNotExist());
	}

	@Test
	void aPastTimeNeedsAJustificationAndIsMarkedRetroactive() throws Exception {
		UUID id = ticket();
		jdbc.update("UPDATE inc_event SET occurred_at = occurred_at - interval '3 hours'");
		assertProblem(communicate(analyst(), id, body("DIRECTIVOS", "INITIAL", ago(Duration.ofHours(1)), "")), 422,
				"TICKET_JUSTIFICATION_REQUIRED");
		assertProblem(communicate(analyst(), id, body("DIRECTIVOS", "INITIAL", ago(Duration.ofHours(1)), ",\"justification\":\" \"")),
				422, "TICKET_JUSTIFICATION_REQUIRED");
		communicate(analyst(), id, body("DIRECTIVOS", "INITIAL", ago(Duration.ofHours(1)),
				",\"justification\":\"Se aviso por llamada antes de registrar\"")).andExpect(status().isOk())
				.andExpect(jsonPath("$.communications[0].retroactive").value(true))
				.andExpect(jsonPath("$.communications[0].justification").value("Se aviso por llamada antes de registrar"))
				.andExpect(jsonPath("$.timeline[?(@.type=='COMMUNICATION_RECORDED')].retroactive")
						.value(org.hamcrest.Matchers.hasItem(true)));
	}

	@Test
	void aFutureTimeIsRejected() throws Exception {
		UUID id = ticket();
		assertProblem(communicate(analyst(), id, body("EMAIL", "UPDATE", Instant.now().plusSeconds(7200).toString(), "")),
				422, "TICKET_EVENT_INVALID");
		assertThat(count("inc_communication")).isZero();
	}

	@Test
	void aJustificationIsNotKeptForANonRetroactiveSend() throws Exception {
		UUID id = ticket();
		communicate(analyst(), id, body("EMAIL", "CLOSURE", Instant.now().toString(), ",\"justification\":\"sobra\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.communications[0].justification").doesNotExist());
	}

	@Test
	void anEvidenceCanBeLinkedToTheCommunicationButNotToAnotherTicketsOne() throws Exception {
		UUID id = ticket();
		communicate(analyst(), id, body("EMAIL", "INITIAL", Instant.now().toString(), "")).andExpect(status().isOk());
		UUID communication = jdbc.queryForObject("SELECT id FROM inc_communication", UUID.class);

		upload(id, communication).andExpect(status().isOk())
				.andExpect(jsonPath("$.evidence[0].communicationId").value(communication.toString()));
		assertThat(jdbc.queryForObject("SELECT communication_id FROM inc_evidence", UUID.class)).isEqualTo(communication);

		UUID other = createEquipmentTicket(analyst(), Feature010Fixtures.JAC_POINT, "DISCONNECTION");
		assertProblem(upload(other, communication), 422, "TICKET_EVENT_INVALID");
		assertProblem(upload(id, UUID.randomUUID()), 422, "TICKET_EVENT_INVALID");
		assertThat(count("inc_evidence")).isEqualTo(1);
	}

	private ResultActions upload(UUID id, UUID communicationId) throws Exception {
		byte[] pdf = "%PDF-1.7\n captura".getBytes(StandardCharsets.US_ASCII);
		MockMultipartFile file = new MockMultipartFile("file", "envio.pdf", "application/pdf", pdf);
		MockMultipartFile meta = new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
				"{\"version\":%d,\"purpose\":\"GENERAL\",\"communicationId\":\"%s\"}".formatted(versionOf(id), communicationId)
						.getBytes(StandardCharsets.UTF_8));
		return mvc.perform(MockMvcRequestBuilders.multipart(BASE + "/tickets/" + id + "/evidence").file(file).file(meta)
				.cookie(analyst()).with(WebSessions.xsrf()));
	}

	@Test
	void annullingTheEventAnnulsTheCommunicationAndNothingIsDeleted() throws Exception {
		UUID id = ticket();
		communicate(analyst(), id, body("EMAIL", "INITIAL", Instant.now().toString(), "")).andExpect(status().isOk());
		UUID event = jdbc.queryForObject("SELECT id FROM inc_event WHERE type = 'COMMUNICATION_RECORDED'", UUID.class);

		assertProblem(post(analyst(), "/tickets/" + id + "/events/" + event + "/annul",
				"{\"version\":%d,\"reason\":\" \"}".formatted(versionOf(id))), 400, "VALIDATION_FAILED");
		post(analyst(), "/tickets/" + id + "/events/" + event + "/annul",
				"{\"version\":%d,\"reason\":\"Se registro en el ticket equivocado\"}".formatted(versionOf(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.communications.length()").value(1))
				.andExpect(jsonPath("$.communications[0].annulled").value(true))
				.andExpect(jsonPath("$.communications[0].annulReason").value("Se registro en el ticket equivocado"));
		assertThat(count("inc_communication")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT annulled_at IS NOT NULL FROM inc_communication", Boolean.class)).isTrue();
	}

	@Test
	void theReadOnlyRolesCannotRecordAndAStaleVersionIs409() throws Exception {
		UUID id = ticket();
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(communicate(reader, id, body("EMAIL", "INITIAL", Instant.now().toString(), "")), 403, "ACCESS_DENIED");
		}
		assertProblem(post(analyst(), "/tickets/" + id + "/communications",
				"{\"version\":%d,%s}".formatted(versionOf(id) + 4, body("EMAIL", "INITIAL", Instant.now().toString(), ""))), 409,
				"TICKET_VERSION_CONFLICT");
		assertThat(count("inc_communication")).isZero();
	}

	@Test
	void invalidInputIsA400() throws Exception {
		UUID id = ticket();
		assertProblem(communicate(analyst(), id, "\"channel\":\"EMAIL\",\"kind\":\"INITIAL\""), 400, "VALIDATION_FAILED");
		communicate(analyst(), id, body("TELEGRAM", "INITIAL", Instant.now().toString(), "")).andExpect(status().isBadRequest());
	}
}
