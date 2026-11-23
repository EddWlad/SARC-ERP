package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

import jakarta.servlet.http.Cookie;

/** Hitos, eventos, prioridad, responsable, relevo, reclasificacion y correccion de datos (FR-031 a FR-046). */
class TicketFollowUpApiTest extends IncidentApiSupport {

	private static final UUID KIA = Feature010Fixtures.KIA_STATION;

	private UUID ticket() throws Exception {
		return createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
	}

	private String milestonePath(UUID id, String kind) {
		return "/tickets/" + id + "/episodes/1/milestones/" + kind;
	}

	private ResultActions milestone(Cookie session, UUID id, String kind, String body) throws Exception {
		return put(session, milestonePath(id, kind), body);
	}

	private String mbody(UUID id, String fields) {
		return "{\"version\":%d,%s}".formatted(versionOf(id), fields);
	}

	private static String ago(Duration duration) {
		return Instant.now().minus(duration).toString();
	}

	// ----- Hitos -----

	@Test
	void recordsAPendingMilestoneAtTheCurrentTimeWithASingleAction() throws Exception {
		UUID id = ticket();
		milestone(analyst(), id, "T1", mbody(id, "\"status\":\"RECORDED\"")).andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].milestones[1].kind").value("T1"))
				.andExpect(jsonPath("$.episodes[0].milestones[1].status").value("RECORDED"))
				.andExpect(jsonPath("$.episodes[0].milestones[1].retroactive").value(false))
				.andExpect(jsonPath("$.episodes[0].durations[0].pair").value("T1-T0"))
				.andExpect(jsonPath("$.episodes[0].durations[0].minutes").value(0))
				.andExpect(jsonPath("$.episodes[0].durations[1].minutes").doesNotExist())
				.andExpect(jsonPath("$.episodes[0].durations[1].missing[0]").value("T2"))
				.andExpect(jsonPath("$.timeline[?(@.type=='MILESTONE_RECORDED')]").isNotEmpty());
		assertThat(count("inc_milestone_revision")).isEqualTo(1);
	}

	@Test
	void aRetroactiveTimeNeedsAReasonAndIsMarked() throws Exception {
		UUID id = ticket();
		Instant t0 = jdbc.queryForObject("SELECT occurred_at FROM inc_milestone WHERE kind = 'T0'", java.sql.Timestamp.class)
				.toInstant();
		jdbc.update("UPDATE inc_milestone SET occurred_at = ? WHERE kind = 'T0'",
				java.sql.Timestamp.from(t0.minus(Duration.ofHours(2))));
		String earlier = "\"status\":\"RECORDED\",\"occurredAt\":\"" + ago(Duration.ofMinutes(30))
				+ "\",\"reason\":\"Aviso tardio\"";
		// T0 queda dos horas atras: una hora anterior a T0 se rechaza.
		assertProblem(milestone(analyst(), id, "T1", mbody(id, "\"status\":\"RECORDED\",\"occurredAt\":\""
				+ ago(Duration.ofHours(3)) + "\",\"reason\":\"x\"")), 422, "MILESTONE_BEFORE_T0");
		milestone(analyst(), id, "T1", mbody(id, earlier)).andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].milestones[1].retroactive").value(true));
		assertProblem(milestone(analyst(), id, "T2", mbody(id, "\"status\":\"RECORDED\",\"occurredAt\":\""
				+ Instant.now().plusSeconds(3600) + "\"")), 422, "MILESTONE_IN_FUTURE");
	}

	@Test
	void anIncompatibleStateAndMissingReasonsAreRejected() throws Exception {
		UUID id = ticket();
		assertProblem(milestone(analyst(), id, "T2", mbody(id, "\"status\":\"NOT_APPLICABLE\"")), 422,
				"MILESTONE_REASON_REQUIRED");
		assertProblem(milestone(analyst(), id, "T3", mbody(id, "\"status\":\"NO_DATA\",\"reason\":\" \"")), 422,
				"MILESTONE_REASON_REQUIRED");
		milestone(analyst(), id, "T2", mbody(id, "\"status\":\"NOT_APPLICABLE\",\"reason\":\"No hubo apoyo\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.episodes[0].milestones[2].status").value("NOT_APPLICABLE"))
				.andExpect(jsonPath("$.episodes[0].milestones[2].occurredAt").doesNotExist());
		assertProblem(milestone(analyst(), id, "T4", mbody(id, "\"status\":\"NO_DATA\",\"reason\":\"x\"")), 422,
				"MILESTONE_T4_NOT_APPLICABLE");
		assertProblem(milestone(analyst(), id, "T4", mbody(id, "\"status\":\"RECORDED\"")), 409, "EPISODE_NOT_EDITABLE");
		assertProblem(milestone(analyst(), id, "T1", mbody(id, "\"status\":\"PENDING\"")), 422, "MILESTONE_INVALID");
	}

	@Test
	void anOutOfOrderSequenceNeedsTheConfirmationAndStoresIt() throws Exception {
		UUID id = ticket();
		Instant t0 = jdbc.queryForObject("SELECT occurred_at FROM inc_milestone WHERE kind = 'T0'", java.sql.Timestamp.class)
				.toInstant();
		// T0 es "ahora": las horas validas caen entre T0 y el momento del registro; se retrasa T0 para dar espacio.
		jdbc.update("UPDATE inc_milestone SET occurred_at = ? WHERE kind = 'T0'",
				java.sql.Timestamp.from(t0.minus(Duration.ofHours(2))));
		milestone(analyst(), id, "T2", mbody(id, "\"status\":\"RECORDED\",\"occurredAt\":\"" + ago(Duration.ofHours(1))
				+ "\",\"reason\":\"Retroactivo\"")).andExpect(status().isOk());
		String t1 = "\"status\":\"RECORDED\",\"occurredAt\":\"" + ago(Duration.ofMinutes(10)) + "\",\"reason\":\"Retroactivo\"";
		assertProblem(milestone(analyst(), id, "T1", mbody(id, t1)), 422, "MILESTONE_ORDER_UNCONFIRMED");
		milestone(analyst(), id, "T1", mbody(id, t1 + ",\"orderWarningAck\":true")).andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].milestones[1].orderWarningAck").value(true));
	}

	@Test
	void aCorrectionNeedsAJustificationAndKeepsTheRevisionHistory() throws Exception {
		UUID id = ticket();
		milestone(analyst(), id, "T1", mbody(id, "\"status\":\"RECORDED\"")).andExpect(status().isOk());
		assertProblem(milestone(analyst(), id, "T1", mbody(id, "\"status\":\"NO_DATA\",\"reason\":\"Se desconoce\"")), 422,
				"MILESTONE_REASON_REQUIRED");
		milestone(analyst(), id, "T1", mbody(id, "\"status\":\"NO_DATA\",\"reason\":\"Se desconoce\","
				+ "\"justification\":\"La hora real no se pudo confirmar\"")).andExpect(status().isOk())
				.andExpect(jsonPath("$.episodes[0].milestones[1].status").value("NO_DATA"))
				.andExpect(jsonPath("$.timeline[?(@.type=='MILESTONE_CORRECTED')]").isNotEmpty());
		assertThat(count("inc_milestone_revision")).as("anexa, no reemplaza").isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT previous_status FROM inc_milestone_revision ORDER BY recorded_at DESC LIMIT 1",
				String.class)).isEqualTo("RECORDED");
	}

	@Test
	void aStaleVersionIsA409AndNothingChanges() throws Exception {
		UUID id = ticket();
		long version = versionOf(id);
		assertProblem(milestone(analyst(), id, "T1", "{\"version\":%d,\"status\":\"RECORDED\"}".formatted(version + 5)), 409,
				"TICKET_VERSION_CONFLICT");
		milestone(analyst(), id, "T1", "{\"version\":%d,\"status\":\"RECORDED\"}".formatted(version)).andExpect(status().isOk());
		assertProblem(milestone(secondAnalyst(), id, "T2", "{\"version\":%d,\"status\":\"RECORDED\"}".formatted(version)), 409,
				"TICKET_VERSION_CONFLICT");
		assertThat(jdbc.queryForObject("SELECT status FROM inc_milestone WHERE kind = 'T2'", String.class))
				.isEqualTo("PENDING");
	}

	// ----- Eventos -----

	private ResultActions event(Cookie session, UUID id, String fields) throws Exception {
		return post(session, "/tickets/" + id + "/events", mbody(id, fields));
	}

	@Test
	void addsManualEventsAndRejectsTheOnesTheSystemGenerates() throws Exception {
		UUID id = ticket();
		for (String type : new String[] { "VALIDATION", "CORRECTIVE_ACTION", "OBSERVATION" }) {
			event(analyst(), id, "\"type\":\"" + type + "\",\"text\":\"Detalle de " + type + "\"").andExpect(status().isOk());
		}
		assertProblem(event(analyst(), id, "\"type\":\"TICKET_CLOSED\",\"text\":\"x\""), 422, "TICKET_EVENT_INVALID");
		assertProblem(event(analyst(), id, "\"type\":\"PRIORITY_CHANGED\",\"text\":\"x\""), 422, "TICKET_EVENT_INVALID");
		assertProblem(event(analyst(), id, "\"type\":\"OBSERVATION\",\"text\":\" \""), 400, "VALIDATION_FAILED");
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.timeline.length()").value(4));
	}

	@Test
	void anEscalationNeedsItsDestinationAndFeedsTheReference() throws Exception {
		UUID id = createEquipmentTicket(analyst(), KIA, "WAIT_FOR_CONNECTION");
		assertProblem(event(analyst(), id, "\"type\":\"ESCALATED\",\"text\":\"Se escalo\""), 422, "TICKET_EVENT_INVALID");
		event(analyst(), id, "\"type\":\"ESCALATED\",\"text\":\"Se escalo a tecnicos\",\"targetKind\":\"TECHNICAL\","
				+ "\"targetRoleOrProvider\":\"Proveedor X\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.timeline[?(@.type=='ESCALATED')].targetKind").value(org.hamcrest.Matchers.hasItem("TECHNICAL")))
				.andExpect(jsonPath("$.episodes[0].references[?(@.measure=='ESCALATION')].result")
						.value(org.hamcrest.Matchers.hasItem("WITHIN")));
		assertThat(jdbc.queryForObject("SELECT notifiable FROM inc_event WHERE type = 'ESCALATED'", Boolean.class)).isTrue();
	}

	@Test
	void aRetroactiveEventIsMarkedAndAFutureOneIsRejected() throws Exception {
		UUID id = ticket();
		event(analyst(), id, "\"type\":\"OBSERVATION\",\"text\":\"Antes\",\"occurredAt\":\"" + ago(Duration.ofHours(1)) + "\"")
				.andExpect(status().isOk()).andExpect(jsonPath("$.timeline[?(@.text=='Antes')].retroactive")
						.value(org.hamcrest.Matchers.hasItem(true)));
		assertProblem(event(analyst(), id, "\"type\":\"OBSERVATION\",\"text\":\"Futuro\",\"occurredAt\":\""
				+ Instant.now().plusSeconds(7200) + "\""), 422, "TICKET_EVENT_INVALID");
	}

	@Test
	void anEventIsAnnulledWithAReasonAndNeverDeleted() throws Exception {
		UUID id = ticket();
		event(analyst(), id, "\"type\":\"OBSERVATION\",\"text\":\"Por error\"").andExpect(status().isOk());
		UUID eventId = jdbc.queryForObject("SELECT id FROM inc_event WHERE type = 'OBSERVATION'", UUID.class);

		assertProblem(post(analyst(), "/tickets/" + id + "/events/" + eventId + "/annul", mbody(id, "\"reason\":\" \"")),
				400, "VALIDATION_FAILED");
		post(analyst(), "/tickets/" + id + "/events/" + eventId + "/annul", mbody(id, "\"reason\":\"Registro duplicado\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeline[?(@.id=='" + eventId + "')].annulled").value(org.hamcrest.Matchers.hasItem(true)))
				.andExpect(jsonPath("$.timeline[?(@.id=='" + eventId + "')].annulReason")
						.value(org.hamcrest.Matchers.hasItem("Registro duplicado")));
		assertThat(count("inc_event")).as("nunca se borra").isEqualTo(2);

		UUID created = jdbc.queryForObject("SELECT id FROM inc_event WHERE type = 'TICKET_CREATED'", UUID.class);
		assertProblem(post(analyst(), "/tickets/" + id + "/events/" + created + "/annul", mbody(id, "\"reason\":\"x\"")), 422,
				"TICKET_EVENT_INVALID");
	}

	// ----- Prioridad, responsable, relevo, reclasificacion, correccion -----

	@Test
	void changesThePriorityWithAReasonAndNotifiesWhenItBecomesCritical() throws Exception {
		UUID id = ticket();
		assertProblem(post(analyst(), "/tickets/" + id + "/priority", mbody(id, "\"priority\":\"CRITICAL\"")), 400,
				"VALIDATION_FAILED");
		post(analyst(), "/tickets/" + id + "/priority", mbody(id, "\"priority\":\"CRITICAL\",\"reason\":\"Afecta una electrolinera\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.priority").value("CRITICAL"));
		assertThat(jdbc.queryForObject("SELECT notifiable FROM inc_event WHERE type = 'PRIORITY_CHANGED'", Boolean.class)).isTrue();
		assertProblem(post(analyst(), "/tickets/" + id + "/priority", mbody(id, "\"priority\":\"CRITICAL\",\"reason\":\"x\"")), 422,
				"TICKET_EVENT_INVALID");
		post(analyst(), "/tickets/" + id + "/priority", mbody(id, "\"priority\":\"MEDIUM\",\"reason\":\"Resuelto\""))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT notifiable FROM inc_event WHERE type = 'PRIORITY_CHANGED' ORDER BY recorded_at",
				Boolean.class)).containsExactly(true, false);
	}

	@Test
	void onlyAnActiveEditorCanBeTheResponsibleAndTheNameFollows() throws Exception {
		UUID id = ticket();
		UserAccount inactive = UserAccount.create(new Email("inactivo@qa010.example.test"), "Inactiva", Set.of(Role.ANALISTA),
				"hash");
		inactive.deactivate();
		users.save(inactive);

		assertProblem(post(analyst(), "/tickets/" + id + "/responsible", mbody(id, "\"responsibleId\":\"" + userId(Role.GERENTE) + "\"")),
				422, "TICKET_RESPONSIBLE_INVALID");
		assertProblem(post(analyst(), "/tickets/" + id + "/responsible", mbody(id, "\"responsibleId\":\"" + inactive.getId() + "\"")),
				422, "TICKET_RESPONSIBLE_INVALID");
		assertProblem(post(analyst(), "/tickets/" + id + "/responsible", mbody(id, "\"responsibleId\":\"" + UUID.randomUUID() + "\"")),
				422, "TICKET_RESPONSIBLE_INVALID");

		post(analyst(), "/tickets/" + id + "/responsible", mbody(id, "\"responsibleId\":\"" + secondAnalystId() + "\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.responsible.name").value("Segundo Analista"));
		assertThat(jdbc.queryForObject("SELECT current_responsible_name FROM inc_ticket", String.class)).isEqualTo("Segundo Analista");
	}

	@Test
	void aHandoverKeepsOutgoingIncomingAndTheFourFields() throws Exception {
		UUID id = ticket();
		String body = "\"incomingResponsibleId\":\"%s\",\"currentStatus\":\"En observacion\",\"pending\":\"Confirmar estabilidad\","
				+ "\"nextCheckAt\":\"%s\",\"closureCriterion\":\"Dos horas estable\"";
		assertProblem(post(analyst(), "/tickets/" + id + "/handover", mbody(id, "\"incomingResponsibleId\":\"" + secondAnalystId()
				+ "\",\"currentStatus\":\"x\",\"pending\":\" \",\"closureCriterion\":\"y\"")), 400, "VALIDATION_FAILED");
		assertProblem(post(analyst(), "/tickets/" + id + "/handover", mbody(id, body.formatted(userId(Role.GERENTE),
				Instant.now().plusSeconds(3600)))), 422, "TICKET_RESPONSIBLE_INVALID");

		post(analyst(), "/tickets/" + id + "/handover", mbody(id, body.formatted(secondAnalystId(), Instant.now().plusSeconds(3600))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.responsible.name").value("Segundo Analista"))
				.andExpect(jsonPath("$.timeline[?(@.type=='SHIFT_HANDOVER')].details.currentStatus")
						.value(org.hamcrest.Matchers.hasItem("En observacion")))
				.andExpect(jsonPath("$.timeline[?(@.type=='SHIFT_HANDOVER')].details.outgoingResponsibleId")
						.value(org.hamcrest.Matchers.hasItem(userId(Role.ANALISTA).toString())))
				.andExpect(jsonPath("$.timeline[?(@.type=='SHIFT_HANDOVER')].details.incomingResponsibleName")
						.value(org.hamcrest.Matchers.hasItem("Segundo Analista")))
				.andExpect(jsonPath("$.timeline[?(@.type=='SHIFT_HANDOVER')].details.outgoingResponsibleName")
						.value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.notNullValue())))
				.andExpect(jsonPath("$.timeline[?(@.type=='SHIFT_HANDOVER')].details.incomingResponsibleId")
						.value(org.hamcrest.Matchers.hasItem(secondAnalystId().toString())));
		assertThat(count("inc_ticket")).as("el relevo no crea un ticket").isEqualTo(1);
	}

	@Test
	void reclassifyingRecalculatesTheProposalWithoutChangingTheCurrentPriority() throws Exception {
		UUID id = ticket();
		post(analyst(), "/tickets/" + id + "/reclassify", mbody(id, "\"incidentType\":\"CONNECTOR_STUCK\",\"reason\":\"Era un conector retenido\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.incidentType").value("CONNECTOR_STUCK"))
				.andExpect(jsonPath("$.summary.priority").value("HIGH"))
				.andExpect(jsonPath("$.timeline[?(@.type=='RECLASSIFIED')].details.proposedPriority")
						.value(org.hamcrest.Matchers.hasItem("CRITICAL")));
		assertProblem(post(analyst(), "/tickets/" + id + "/reclassify", mbody(id, "\"incidentType\":\"CHATBOT_SERVICE_FAILURE\",\"reason\":\"x\"")),
				422, "TICKET_ELEMENT_INVALID");
		assertProblem(post(analyst(), "/tickets/" + id + "/reclassify", mbody(id, "\"incidentType\":\"CONNECTOR_STUCK\",\"reason\":\"x\"")),
				422, "TICKET_EVENT_INVALID");
	}

	@Test
	void correctingDataNeedsAJustificationAndLeavesOneEventPerField() throws Exception {
		UUID id = ticket();
		assertProblem(patch(analyst(), "/tickets/" + id, mbody(id, "\"description\":\"Nueva\"")), 400, "VALIDATION_FAILED");
		patch(analyst(), "/tickets/" + id, mbody(id, "\"description\":\"Descripcion corregida\",\"reportOrigin\":\"CUSTOMER\","
				+ "\"justification\":\"Dato mal escrito\"")).andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Descripcion corregida"))
				.andExpect(jsonPath("$.reportOrigin").value("CUSTOMER"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_event WHERE type = 'DATA_CORRECTED'", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT details ->> 'previous' FROM inc_event WHERE type = 'DATA_CORRECTED' AND details ->> 'field' = 'DESCRIPTION'",
				String.class)).isEqualTo("Sin comunicacion");
		assertProblem(patch(analyst(), "/tickets/" + id, mbody(id, "\"justification\":\"x\"")), 422, "TICKET_EVENT_INVALID");
		assertProblem(patch(analyst(), "/tickets/" + id, mbody(id, "\"reportOrigin\":\"OTHER\",\"justification\":\"x\"")), 422,
				"TICKET_EVENT_INVALID");
	}

	@Test
	void theResponsiblesListHasOnlyActiveEditorsAndNoEmail() throws Exception {
		get(analyst(), "/responsibles").andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("@"))))
				.andExpect(jsonPath("$[?(@.name=='Segundo Analista')]").isNotEmpty())
				.andExpect(jsonPath("$[?(@.name=='Prueba GERENTE')]").isEmpty());
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(get(reader, "/responsibles"), 403, "ACCESS_DENIED");
		}
	}

	@Test
	void theReadOnlyRolesCannotWriteAnythingAndNothingChanges() throws Exception {
		UUID id = ticket();
		long version = versionOf(id);
		int events = count("inc_event");
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(milestone(reader, id, "T1", "{\"version\":%d,\"status\":\"RECORDED\"}".formatted(version)), 403, "ACCESS_DENIED");
			assertProblem(event(reader, id, "\"type\":\"OBSERVATION\",\"text\":\"x\""), 403, "ACCESS_DENIED");
			assertProblem(post(reader, "/tickets/" + id + "/priority", mbody(id, "\"priority\":\"CRITICAL\",\"reason\":\"x\"")), 403, "ACCESS_DENIED");
			assertProblem(post(reader, "/tickets/" + id + "/responsible", mbody(id, "\"responsibleId\":\"" + secondAnalystId() + "\"")), 403,
					"ACCESS_DENIED");
			assertProblem(post(reader, "/tickets/" + id + "/reclassify", mbody(id, "\"incidentType\":\"CONNECTOR_STUCK\",\"reason\":\"x\"")), 403,
					"ACCESS_DENIED");
			assertProblem(patch(reader, "/tickets/" + id, mbody(id, "\"description\":\"x\",\"justification\":\"y\"")), 403, "ACCESS_DENIED");
		}
		assertThat(count("inc_event")).isEqualTo(events);
		assertThat(versionOf(id)).isEqualTo(version);
	}

	// ----- Visitas -----

	private ResultActions visitStage(Cookie session, UUID id, UUID visit, String fields) throws Exception {
		return post(session, "/tickets/" + id + "/visits/" + visit + "/stage", mbody(id, fields));
	}

	@Test
	void aVisitAdvancesByStageAndEachStageLeavesItsEvent() throws Exception {
		UUID id = ticket();
		post(analyst(), "/tickets/" + id + "/visits", mbody(id, "\"providerName\":\"Proveedor Uno\",\"note\":\"Se solicita la visita\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.visits.length()").value(1))
				.andExpect(jsonPath("$.visits[0].stage").value("REQUESTED"))
				.andExpect(jsonPath("$.visits[0].providerName").value("Proveedor Uno"))
				.andExpect(jsonPath("$.visits[0].episodeOrdinal").value(1));
		UUID visit = jdbc.queryForObject("SELECT id FROM inc_visit", UUID.class);

		assertProblem(visitStage(analyst(), id, visit, "\"stage\":\"CONFIRMED\""), 422, "VISIT_TRANSITION_INVALID");
		visitStage(analyst(), id, visit, "\"stage\":\"ASSIGNED\",\"providerName\":\"Proveedor Dos\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.visits[0].stage").value("ASSIGNED"))
				.andExpect(jsonPath("$.visits[0].providerName").value("Proveedor Dos"));
		visitStage(analyst(), id, visit, "\"stage\":\"CONFIRMED\"").andExpect(status().isOk());
		visitStage(analyst(), id, visit, "\"stage\":\"EXECUTED\"").andExpect(status().isOk());
		visitStage(analyst(), id, visit, "\"stage\":\"REPORT_RECEIVED\",\"note\":\"Informe recibido\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.visits[0].stage").value("REPORT_RECEIVED"));
		assertProblem(visitStage(analyst(), id, visit, "\"stage\":\"CANCELLED\",\"cancelDecidedBy\":\"x\","
				+ "\"cancelEvidenceNote\":\"y\",\"reescalationCondition\":\"z\""), 422, "VISIT_TRANSITION_INVALID");

		assertThat(jdbc.queryForList("SELECT details ->> 'stage' FROM inc_event WHERE type = 'VISIT_STAGE' ORDER BY recorded_at, id",
				String.class)).containsExactlyInAnyOrder("REQUESTED", "ASSIGNED", "CONFIRMED", "EXECUTED", "REPORT_RECEIVED");
		get(analyst(), "/tickets/" + id).andExpect(status().isOk())
				.andExpect(jsonPath("$.timeline[?(@.type=='VISIT_STAGE')]").value(org.hamcrest.Matchers.hasSize(5)));
	}

	@Test
	void postponingNeedsTheThreeDecisionFieldsAndTheyAreKeptInTheEvent() throws Exception {
		UUID id = ticket();
		post(analyst(), "/tickets/" + id + "/visits", mbody(id, "\"providerName\":\"Proveedor Uno\"")).andExpect(status().isOk());
		UUID visit = jdbc.queryForObject("SELECT id FROM inc_visit", UUID.class);

		assertProblem(visitStage(analyst(), id, visit, "\"stage\":\"POSTPONED\""), 422, "VISIT_TRANSITION_INVALID");
		assertProblem(visitStage(analyst(), id, visit, "\"stage\":\"POSTPONED\",\"cancelDecidedBy\":\"Supervisión\","
				+ "\"cancelEvidenceNote\":\"Correo del proveedor\""), 422, "VISIT_TRANSITION_INVALID");
		visitStage(analyst(), id, visit, "\"stage\":\"POSTPONED\",\"cancelDecidedBy\":\"Supervisión\","
				+ "\"cancelEvidenceNote\":\"Correo del proveedor\",\"reescalationCondition\":\"Si no hay fecha en 48 h\"")
				.andExpect(status().isOk()).andExpect(jsonPath("$.visits[0].stage").value("POSTPONED"))
				.andExpect(jsonPath("$.visits[0].cancelDecidedBy").value("Supervisión"))
				.andExpect(jsonPath("$.visits[0].reescalationCondition").value("Si no hay fecha en 48 h"));
		assertThat(jdbc.queryForObject("SELECT details ->> 'reescalationCondition' FROM inc_event WHERE details ->> 'stage' = 'POSTPONED'",
				String.class)).isEqualTo("Si no hay fecha en 48 h");
		visitStage(analyst(), id, visit, "\"stage\":\"ASSIGNED\"").andExpect(status().isOk());
	}

	@Test
	void aVisitOfAnotherTicketIs404AndTheReadOnlyRolesCannotWriteVisits() throws Exception {
		UUID id = ticket();
		post(analyst(), "/tickets/" + id + "/visits", mbody(id, "\"providerName\":\"Proveedor Uno\"")).andExpect(status().isOk());
		UUID visit = jdbc.queryForObject("SELECT id FROM inc_visit", UUID.class);
		UUID other = createEquipmentTicket(analyst(), Feature010Fixtures.JAC_POINT, "DISCONNECTION");

		assertProblem(visitStage(analyst(), other, visit, "\"stage\":\"ASSIGNED\""), 404, "VISIT_NOT_FOUND");
		assertProblem(visitStage(analyst(), id, UUID.randomUUID(), "\"stage\":\"ASSIGNED\""), 404, "VISIT_NOT_FOUND");
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(post(reader, "/tickets/" + id + "/visits", mbody(id, "\"providerName\":\"X\"")), 403, "ACCESS_DENIED");
			assertProblem(visitStage(reader, id, visit, "\"stage\":\"ASSIGNED\""), 403, "ACCESS_DENIED");
		}
		assertThat(count("inc_visit")).isEqualTo(1);
		assertProblem(post(analyst(), "/tickets/" + id + "/visits", "{\"version\":%d,\"providerName\":\"%s\"}".formatted(versionOf(id),
				"p".repeat(121))), 400, "VALIDATION_FAILED");
	}

	@Test
	void providersAreSuggestedFromThePastVisitsWithoutDuplicatesAndOnlyForEditors() throws Exception {
		UUID first = ticket();
		UUID second = createEquipmentTicket(analyst(), Feature010Fixtures.JAC_POINT, "DISCONNECTION");
		post(analyst(), "/tickets/" + first + "/visits", mbody(first, "\"providerName\":\"Proveedor Uno\"")).andExpect(status().isOk());
		post(analyst(), "/tickets/" + second + "/visits", mbody(second, "\"providerName\":\"Proveedor Uno\"")).andExpect(status().isOk());
		post(analyst(), "/tickets/" + second + "/visits", mbody(second, "\"providerName\":\"Otro Taller\"")).andExpect(status().isOk());
		post(analyst(), "/tickets/" + second + "/visits", mbody(second, "\"note\":\"Sin proveedor aún\"")).andExpect(status().isOk());

		get(analyst(), "/providers").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0]").value("Otro Taller")).andExpect(jsonPath("$[1]").value("Proveedor Uno"));
		get(analyst(), "/providers?search=UNO").andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0]").value("Proveedor Uno"));
		get(analyst(), "/providers?search=%25").andExpect(jsonPath("$.length()").value(0));
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(get(reader, "/providers"), 403, "ACCESS_DENIED");
		}
	}

	@Test
	void theDetailShowsTheElapsedTimeApartAndTheTypeReferenceFlaggedAsNotAContractualSla() throws Exception {
		UUID id = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.episodes[0].elapsedOpenMinutes").value(0))
				.andExpect(jsonPath("$.episodes[0].references[?(@.measure=='RECOVERY')].limitMinutes")
						.value(org.hamcrest.Matchers.hasItem(300)))
				.andExpect(jsonPath("$.episodes[0].references[?(@.measure=='RECOVERY')].extendable")
						.value(org.hamcrest.Matchers.hasItem(true)))
				.andExpect(jsonPath("$.episodes[0].references[?(@.measure=='RECOVERY')].result")
						.value(org.hamcrest.Matchers.hasItem("NOT_EVALUABLE")));
		UUID chatbot = idOf(createTicket(analyst(), chatbotBody("Chatbot caido", "")).andExpect(status().isCreated()));
		get(analyst(), "/tickets/" + chatbot).andExpect(jsonPath("$.episodes[0].references.length()").value(0));
	}
}
