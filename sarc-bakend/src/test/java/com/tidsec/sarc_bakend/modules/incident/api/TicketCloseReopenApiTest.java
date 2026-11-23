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

import org.junit.jupiter.api.AfterEach;
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

/** Cierre y reapertura (FR-044, FR-047 a FR-049): los requisitos por clase, el episodio nuevo y los bloqueos. */
class TicketCloseReopenApiTest extends IncidentApiSupport {

	private static final String CHECKS = "\"closureChecks\":{\"platformNormal\":\"CONFIRMED\",\"stabilityChecked\":\"CONFIRMED\","
			+ "\"noAffectedUser\":\"CONFIRMED\",\"postTest\":\"NOT_APPLICABLE\"}";
	private static final String WAIVERS = "\"finalEvidenceWaiver\":\"Sin captura disponible\","
			+ "\"closureCommunicationWaiver\":\"Se aviso por llamada\"";
	private static final String RESULT = "\"result\":\"RECOVERED\",\"causeKind\":\"CONFIRMED\",\"causeText\":\"Falla del enlace\"";

	@BeforeEach
	void storage() {
		when(fileStorage.storeDocument(anyString(), anyString(), any(byte[].class))).thenReturn("opaque-ref");
	}

	/** La version de referencia extra de una prueba no debe sobrevivir: la base se comparte entre clases de prueba. */
	@AfterEach
	void dropExtraReferenceVersions() {
		Feature010Fixtures.clearIncidents(jdbc);
		jdbc.update("DELETE FROM inc_reference_rule WHERE version_id IN (SELECT id FROM inc_reference_version WHERE code = 'P2')");
		jdbc.update("DELETE FROM inc_reference_version WHERE code = 'P2'");
	}

	private UUID equipmentTicket() throws Exception {
		return createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
	}

	private UUID closedEquipmentTicket() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);
		close(analyst(), id, RESULT + "," + CHECKS + "," + WAIVERS).andExpect(status().isOk());
		return id;
	}

	// ----- Cierre -----

	@Test
	void aValidClosureOfAnEquipmentRecordsT4TheResultAndTheCauseAndLeavesANotifiableEvent() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);

		close(analyst(), id, RESULT + ",\"recommendations\":\"Revisar el enlace\"," + CHECKS + "," + WAIVERS)
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.state").value("CLOSED"))
				.andExpect(jsonPath("$.episodes[0].state").value("CLOSED"))
				.andExpect(jsonPath("$.episodes[0].milestones[4].status").value("RECORDED"))
				.andExpect(jsonPath("$.episodes[0].closure.result").value("RECOVERED"))
				.andExpect(jsonPath("$.episodes[0].closure.causeKind").value("CONFIRMED"))
				.andExpect(jsonPath("$.episodes[0].closure.causeText").value("Falla del enlace"))
				.andExpect(jsonPath("$.episodes[0].closure.recommendations").value("Revisar el enlace"))
				.andExpect(jsonPath("$.episodes[0].closure.closureChecks.postTest").value("NOT_APPLICABLE"))
				.andExpect(jsonPath("$.episodes[0].closure.finalEvidenceWaiver").value("Sin captura disponible"))
				.andExpect(jsonPath("$.timeline[?(@.type=='TICKET_CLOSED')]").isNotEmpty());

		assertThat(jdbc.queryForObject("SELECT notifiable FROM inc_event WHERE type = 'TICKET_CLOSED'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("SELECT state FROM inc_ticket", String.class)).isEqualTo("CLOSED");
		assertThat(count("inc_milestone_revision")).as("T1 a T3 y T4").isEqualTo(4);
	}

	@Test
	void aTicketCanBeClosedAfterRecordingExactHoursWithoutDependingOnTheClock() throws Exception {
		UUID id = equipmentTicket();
		backdateT0(id, 180);
		recordMilestoneAt(analyst(), id, "T1", 4).andExpect(status().isOk());
		recordMilestoneAt(analyst(), id, "T2", 10).andExpect(status().isOk());
		recordMilestoneAt(analyst(), id, "T3", 60).andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].milestones[3].status").value("RECORDED"));

		close(analyst(), id, RESULT + "," + CHECKS + "," + WAIVERS).andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.state").value("CLOSED"));
	}

	@Test
	void aChatbotAndASiteElementCloseWithoutTheChargerChecks() throws Exception {
		UUID chatbot = createChatbotTicket(analyst());
		UUID site = createSiteTicket(analyst());

		for (UUID id : new UUID[] { chatbot, site }) {
			resolveMilestones(id);
			close(analyst(), id, RESULT + "," + WAIVERS).andExpect(status().isOk())
					.andExpect(jsonPath("$.summary.state").value("CLOSED"))
					.andExpect(jsonPath("$.episodes[0].closure.closureChecks").isEmpty());
		}
		assertThat(jdbc.queryForList("SELECT state FROM inc_ticket", String.class)).containsOnly("CLOSED").hasSize(2);
	}

	@Test
	void aClosureWithMissingRequirementsIs422WithTheCompleteListAndChangesNothing() throws Exception {
		UUID id = equipmentTicket();
		int events = count("inc_event");
		long version = versionOf(id);

		close(analyst(), id, "").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("TICKET_CLOSE_REQUIREMENTS"))
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("T1", "T2", "T3", "RESULT", "CAUSE",
						"CLOSURE_CHECKS", "FINAL_EVIDENCE", "CLOSURE_COMMUNICATION")));

		assertThat(count("inc_event")).isEqualTo(events);
		assertThat(versionOf(id)).isEqualTo(version);
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.summary.state").value("OPEN"))
				.andExpect(jsonPath("$.episodes[0].closure").doesNotExist());
	}

	@Test
	void aConfirmedCauseNeedsItsDescriptionAndAProbableOneNeedsAnEvidenceOfTheEpisode() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);
		String base = "\"result\":\"RECOVERED\"," + CHECKS + "," + WAIVERS;

		close(analyst(), id, base + ",\"causeKind\":\"CONFIRMED\"").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("CAUSE_TEXT")));
		close(analyst(), id, base + ",\"causeKind\":\"PROBABLE\",\"causeText\":\"Posible falla\"")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("PROBABLE_CAUSE_EVIDENCE")));

		upload(id, "GENERAL").andExpect(status().isOk());
		close(analyst(), id, base + ",\"causeKind\":\"PROBABLE\",\"causeText\":\"Posible falla\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].closure.causeKind").value("PROBABLE"));
	}

	@Test
	void anUndeterminedCauseNeedsNoDescription() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);
		close(analyst(), id, "\"result\":\"NO_FAULT_CONFIRMED\",\"causeKind\":\"UNDETERMINED\"," + CHECKS + "," + WAIVERS)
				.andExpect(status().isOk()).andExpect(jsonPath("$.episodes[0].closure.causeText").doesNotExist());
	}

	@Test
	void aFinalEvidenceReplacesItsWaiverAndAnAnnulledOneDoesNot() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);
		String base = RESULT + "," + CHECKS + ",\"closureCommunicationWaiver\":\"Se aviso por llamada\"";

		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("FINAL_EVIDENCE")));
		upload(id, "GENERAL").andExpect(status().isOk());
		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("FINAL_EVIDENCE")));

		upload(id, "FINAL").andExpect(status().isOk());
		UUID finalEvent = jdbc.queryForObject("SELECT event_id FROM inc_evidence WHERE purpose = 'FINAL'", UUID.class);
		post(analyst(), "/tickets/" + id + "/events/" + finalEvent + "/annul",
				"{\"version\":%d,\"reason\":\"Archivo equivocado\"}".formatted(versionOf(id))).andExpect(status().isOk());
		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("FINAL_EVIDENCE")));

		upload(id, "FINAL").andExpect(status().isOk());
		close(analyst(), id, base).andExpect(status().isOk());
	}

	@Test
	void aClosureCommunicationReplacesItsWaiver() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);
		String base = RESULT + "," + CHECKS + ",\"finalEvidenceWaiver\":\"Sin captura\"";

		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("CLOSURE_COMMUNICATION")));
		post(analyst(), "/tickets/" + id + "/communications", "{\"version\":%d,\"channel\":\"EMAIL\",\"kind\":\"UPDATE\",\"sentAt\":\"%s\"}"
				.formatted(versionOf(id), Instant.now())).andExpect(status().isOk());
		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("CLOSURE_COMMUNICATION")));
		post(analyst(), "/tickets/" + id + "/communications", "{\"version\":%d,\"channel\":\"EMAIL\",\"kind\":\"CLOSURE\",\"sentAt\":\"%s\"}"
				.formatted(versionOf(id), Instant.now())).andExpect(status().isOk());
		close(analyst(), id, base).andExpect(status().isOk());
	}

	@Test
	void aDeviationFromTheReferenceNeedsItsJustification() throws Exception {
		UUID id = equipmentTicket();
		// T0 siete horas atras y recuperacion ahora: 420 min, por encima de los 300 de la referencia.
		jdbc.update("UPDATE inc_milestone SET occurred_at = occurred_at - interval '7 hours' WHERE kind = 'T0'");
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T1", "{\"version\":%d,\"status\":\"NOT_APPLICABLE\",\"reason\":\"x\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T2", "{\"version\":%d,\"status\":\"NOT_APPLICABLE\",\"reason\":\"x\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T3", "{\"version\":%d,\"status\":\"RECORDED\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		String base = RESULT + "," + CHECKS + "," + WAIVERS;

		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("DEVIATION_JUSTIFICATION")));
		close(analyst(), id, base + ",\"deviationJustification\":\"Se esperó al proveedor\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].closure.deviationJustification").value("Se esperó al proveedor"));
	}

	@Test
	void t4CannotBeInTheFutureBeforeAnotherMilestoneOrRetroactiveWithoutAReason() throws Exception {
		UUID id = equipmentTicket();
		jdbc.update("UPDATE inc_milestone SET occurred_at = occurred_at - interval '3 hours' WHERE kind = 'T0'");
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T1", "{\"version\":%d,\"status\":\"RECORDED\",\"occurredAt\":\"%s\",\"reason\":\"Hora real\"}"
				.formatted(versionOf(id), Instant.now().minus(Duration.ofMinutes(170)))).andExpect(status().isOk());
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T2", "{\"version\":%d,\"status\":\"NOT_APPLICABLE\",\"reason\":\"x\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T3", "{\"version\":%d,\"status\":\"NOT_APPLICABLE\",\"reason\":\"x\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		// T1 a las tres horas queda fuera de la referencia de validacion: se justifica para probar solo la hora de T4.
		String base = RESULT + "," + CHECKS + "," + WAIVERS + ",\"deviationJustification\":\"Demora de la prueba\"";

		assertProblem(close(analyst(), id, base + ",\"t4\":\"" + Instant.now().plusSeconds(7200) + "\""), 422, "MILESTONE_IN_FUTURE");
		assertProblem(close(analyst(), id, base + ",\"t4\":\"" + Instant.now().minus(Duration.ofMinutes(175)) + "\""), 422,
				"MILESTONE_T4_BEFORE_MILESTONES");
		assertProblem(close(analyst(), id, base + ",\"t4\":\"" + Instant.now().minus(Duration.ofMinutes(10)) + "\""), 422,
				"MILESTONE_REASON_REQUIRED");
		close(analyst(), id, base + ",\"t4\":\"" + Instant.now().minus(Duration.ofMinutes(10)) + "\",\"t4Reason\":\"Se cerró antes en campo\"")
				.andExpect(status().isOk()).andExpect(jsonPath("$.episodes[0].milestones[4].retroactive").value(true));
		assertThat(jdbc.queryForObject("SELECT state FROM inc_ticket", String.class)).isEqualTo("CLOSED");
	}

	@Test
	void closingAClosedTicketIs409() throws Exception {
		UUID id = closedEquipmentTicket();
		assertProblem(close(analyst(), id, RESULT + "," + CHECKS + "," + WAIVERS), 409, "TICKET_ALREADY_CLOSED");
	}

	@Test
	void aDuplicateNeedsAnExistingOriginalThatIsAnotherTicket() throws Exception {
		UUID id = equipmentTicket();
		UUID original = createEquipmentTicket(analyst(), Feature010Fixtures.JAC_POINT, "DISCONNECTION");
		String base = "\"result\":\"DUPLICATE\",\"causeKind\":\"UNDETERMINED\"";

		close(analyst(), id, base).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.missing").value(org.hamcrest.Matchers.contains("DUPLICATE_OF")));
		assertProblem(close(analyst(), id, base + ",\"duplicateOfTicketId\":\"" + id + "\""), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(close(analyst(), id, base + ",\"duplicateOfTicketId\":\"" + UUID.randomUUID() + "\""), 422,
				"TICKET_ELEMENT_INVALID");
		close(analyst(), id, base + ",\"duplicateOfTicketId\":\"" + original + "\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].closure.duplicateOfTicketId").value(original.toString()))
				.andExpect(jsonPath("$.episodes[0].closure.duplicateOfCode").value("TICKET-9002"));
	}

	@Test
	void aStaleVersionIs409AndTheReadOnlyRolesCannotCloseOrReopen() throws Exception {
		UUID id = equipmentTicket();
		resolveMilestones(id);
		assertProblem(post(analyst(), "/tickets/" + id + "/close",
				"{\"version\":%d,%s,%s,%s}".formatted(versionOf(id) + 3, RESULT, CHECKS, WAIVERS)), 409, "TICKET_VERSION_CONFLICT");
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(close(reader, id, RESULT + "," + CHECKS + "," + WAIVERS), 403, "ACCESS_DENIED");
			assertProblem(reopen(reader, id, "\"reason\":\"x\""), 403, "ACCESS_DENIED");
		}
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.summary.state").value("OPEN"));
	}

	// ----- Reapertura -----

	@Test
	void reopeningCreatesEpisode2WithItsOwnT0AndKeepsTheCodeAndEpisode1() throws Exception {
		UUID id = closedEquipmentTicket();
		String episodeOne = jdbc.queryForObject("SELECT closure_result || ':' || state FROM inc_episode WHERE ordinal = 1", String.class);

		reopen(analyst(), id, "\"reason\":\"La falla volvio\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.code").value("TICKET-9001")).andExpect(jsonPath("$.summary.state").value("OPEN"))
				.andExpect(jsonPath("$.episodes.length()").value(2)).andExpect(jsonPath("$.episodes[1].ordinal").value(2))
				.andExpect(jsonPath("$.episodes[1].state").value("OPEN"))
				.andExpect(jsonPath("$.episodes[1].reopenReason").value("La falla volvio"))
				.andExpect(jsonPath("$.episodes[1].milestones[0].kind").value("T0"))
				.andExpect(jsonPath("$.episodes[1].milestones[0].status").value("RECORDED"))
				.andExpect(jsonPath("$.episodes[1].milestones[1].status").value("PENDING"))
				.andExpect(jsonPath("$.episodes[1].milestones[4].status").value("PENDING"))
				.andExpect(jsonPath("$.episodes[1].closure").doesNotExist())
				.andExpect(jsonPath("$.episodes[0].state").value("CLOSED"))
				.andExpect(jsonPath("$.episodes[0].closure.result").value("RECOVERED"))
				.andExpect(jsonPath("$.timeline[?(@.type=='TICKET_REOPENED')]").isNotEmpty());

		assertThat(jdbc.queryForObject("SELECT closure_result || ':' || state FROM inc_episode WHERE ordinal = 1", String.class))
				.as("el episodio 1 no cambia").isEqualTo(episodeOne);
		assertThat(jdbc.queryForObject("SELECT notifiable FROM inc_event WHERE type = 'TICKET_REOPENED'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("SELECT reference_version_id FROM inc_episode WHERE ordinal = 2", UUID.class))
				.isEqualTo(jdbc.queryForObject("SELECT reference_version_id FROM inc_episode WHERE ordinal = 1", UUID.class));
		assertThat(count("inc_ticket")).isEqualTo(1);
	}

	@Test
	void theNewEpisodeUsesTheReferenceVersionInForceAndTheOldOneKeepsItsOwn() throws Exception {
		UUID id = closedEquipmentTicket();
		UUID second = UUID.randomUUID();
		jdbc.update("INSERT INTO inc_reference_version (id, code, label, effective_from, source_note) VALUES (?, 'P2', 'Version de prueba', now() - interval '1 hour', 'Prueba')",
				second);
		jdbc.update("INSERT INTO inc_reference_rule (version_id, incident_type, validation_max_min, recovery_max_min, recovery_extendable, proposed_priority) VALUES (?, 'DISCONNECTION', 5, 150, true, 'HIGH')",
				second);

		reopen(analyst(), id, "\"reason\":\"La falla volvio\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].references[?(@.measure=='RECOVERY')].limitMinutes")
						.value(org.hamcrest.Matchers.hasItem(300)))
				.andExpect(jsonPath("$.episodes[1].references[?(@.measure=='RECOVERY')].limitMinutes")
						.value(org.hamcrest.Matchers.hasItem(150)));
		assertThat(jdbc.queryForObject("SELECT reference_version_id FROM inc_episode WHERE ordinal = 2", UUID.class)).isEqualTo(second);
		assertThat(jdbc.queryForObject("SELECT reference_version_id FROM inc_episode WHERE ordinal = 1", UUID.class)).isNotEqualTo(second);
	}

	@Test
	void reopeningNeedsAReasonAndTheNewT0CannotBeInTheFutureOrBeforeTheClosure() throws Exception {
		UUID id = closedEquipmentTicket();
		reopen(analyst(), id, "\"reason\":\" \"").andExpect(status().isBadRequest());
		assertProblem(reopen(analyst(), id, "\"reason\":\"Vuelve\",\"t0\":\"" + Instant.now().plusSeconds(7200) + "\""), 422,
				"TICKET_T0_INVALID");
		assertProblem(reopen(analyst(), id, "\"reason\":\"Vuelve\",\"t0\":\"" + Instant.now().minus(Duration.ofDays(2)) + "\""), 422,
				"TICKET_T0_INVALID");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_episode", Integer.class)).isEqualTo(1);
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.summary.state").value("CLOSED"));
	}

	@Test
	void aRetroactiveNewDetectionNeedsItsOwnReason() throws Exception {
		UUID id = closedEquipmentTicket();
		jdbc.update("UPDATE inc_milestone SET occurred_at = occurred_at - interval '3 hours' WHERE kind = 'T4'");
		String earlier = "\"t0\":\"" + Instant.now().minus(Duration.ofMinutes(30)) + "\"";
		assertProblem(reopen(analyst(), id, "\"reason\":\"Vuelve\"," + earlier), 422, "TICKET_T0_INVALID");
		reopen(analyst(), id, "\"reason\":\"Vuelve\"," + earlier + ",\"t0Reason\":\"Se detectó antes de registrar\"")
				.andExpect(status().isOk()).andExpect(jsonPath("$.episodes[1].milestones[0].retroactive").value(true));
	}

	@Test
	void aTicketClosedAsDuplicateOrRegisteredInErrorIsNotReopened() throws Exception {
		UUID original = createEquipmentTicket(analyst(), Feature010Fixtures.JAC_POINT, "DISCONNECTION");
		UUID duplicate = equipmentTicket();
		close(analyst(), duplicate, "\"result\":\"DUPLICATE\",\"causeKind\":\"UNDETERMINED\",\"duplicateOfTicketId\":\"" + original + "\"")
				.andExpect(status().isOk());
		assertProblem(reopen(analyst(), duplicate, "\"reason\":\"Vuelve\""), 409, "TICKET_REOPEN_FORBIDDEN");

		UUID wrong = createEquipmentTicket(analyst(), Feature010Fixtures.NO_MAPS, "DISCONNECTION");
		close(analyst(), wrong, "\"result\":\"REGISTERED_IN_ERROR\",\"causeKind\":\"UNDETERMINED\"").andExpect(status().isOk());
		assertProblem(reopen(analyst(), wrong, "\"reason\":\"Vuelve\""), 409, "TICKET_REOPEN_FORBIDDEN");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_episode WHERE ticket_id = ?", Integer.class, wrong)).isEqualTo(1);
	}

	@Test
	void anOpenTicketCannotBeReopened() throws Exception {
		UUID id = equipmentTicket();
		assertProblem(reopen(analyst(), id, "\"reason\":\"Vuelve\""), 409, "TICKET_ALREADY_OPEN");
	}

	@Test
	void theSecondEpisodeClosesOnItsOwnAndTheTicketHoldsBothResults() throws Exception {
		UUID id = closedEquipmentTicket();
		reopen(analyst(), id, "\"reason\":\"La falla volvio\"").andExpect(status().isOk());
		for (String kind : new String[] { "T1", "T2", "T3" }) {
			put(analyst(), "/tickets/" + id + "/episodes/2/milestones/" + kind,
					"{\"version\":%d,\"status\":\"NOT_APPLICABLE\",\"reason\":\"x\"}".formatted(versionOf(id))).andExpect(status().isOk());
		}
		close(analyst(), id, "\"result\":\"NO_FAULT_CONFIRMED\",\"causeKind\":\"UNDETERMINED\"," + CHECKS + "," + WAIVERS)
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.state").value("CLOSED"))
				.andExpect(jsonPath("$.episodes[0].closure.result").value("RECOVERED"))
				.andExpect(jsonPath("$.episodes[1].closure.result").value("NO_FAULT_CONFIRMED"));
	}

	// ----- Correccion de T4 -----

	@Test
	void t4CanBeCorrectedInAClosedEpisodeOnlyWithAJustification() throws Exception {
		UUID id = closedEquipmentTicket();
		jdbc.update("UPDATE inc_milestone SET occurred_at = occurred_at - interval '3 hours' WHERE kind = 'T0'");
		String earlier = Instant.now().minus(Duration.ofMinutes(5)).toString();
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T4",
				"{\"version\":%d,\"status\":\"RECORDED\",\"occurredAt\":\"%s\",\"reason\":\"Hora real\"}".formatted(versionOf(id), earlier))
				.andExpect(status().isUnprocessableContent());
		put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T4",
				"{\"version\":%d,\"status\":\"RECORDED\",\"occurredAt\":\"%s\",\"reason\":\"Hora real\",\"justification\":\"Se cerró en campo antes\"}"
						.formatted(versionOf(id), earlier))
				.andExpect(status().isOk()).andExpect(jsonPath("$.timeline[?(@.type=='MILESTONE_CORRECTED')]").isNotEmpty())
				.andExpect(jsonPath("$.summary.state").value("CLOSED"));
		assertProblem(put(analyst(), "/tickets/" + id + "/episodes/1/milestones/T4",
				"{\"version\":%d,\"status\":\"NO_DATA\",\"reason\":\"x\",\"justification\":\"y\"}".formatted(versionOf(id))), 422,
				"MILESTONE_T4_NOT_APPLICABLE");
	}

	private ResultActions upload(UUID id, String purpose) throws Exception {
		byte[] pdf = "%PDF-1.7\n evidencia".getBytes(StandardCharsets.US_ASCII);
		MockMultipartFile file = new MockMultipartFile("file", "evidencia.pdf", "application/pdf", pdf);
		MockMultipartFile meta = new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
				"{\"version\":%d,\"purpose\":\"%s\"}".formatted(versionOf(id), purpose).getBytes(StandardCharsets.UTF_8));
		return mvc.perform(MockMvcRequestBuilders.multipart(BASE + "/tickets/" + id + "/evidence").file(file).file(meta)
				.cookie(analyst()).with(WebSessions.xsrf()));
	}
}
