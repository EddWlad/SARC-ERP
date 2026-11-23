package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

import jakarta.servlet.http.Cookie;

/** Listado, detalle y relaciones (FR-017, FR-061, FR-075, D12, P-14; escenarios 8, 10 y 45). */
class TicketReadApiTest extends IncidentApiSupport {

	private static final UUID KIA = Feature010Fixtures.KIA_STATION;
	private static final UUID JAC = Feature010Fixtures.JAC_POINT;

	private List<String> codes(String query) throws Exception {
		String json = get(analyst(), "/tickets" + query).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(json, "$.items[*].summary.code".replace("summary.", ""));
	}

	/** Cuatro tickets: JAC (Alta), KIA (Alta, 2 conectores), chatbot y barrera, en ese orden de creacion. */
	private void seedFour() throws Exception {
		createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		createEquipmentTicket(secondAnalyst(), KIA, "WAIT_FOR_CONNECTION");
		createTicket(analyst(), chatbotBody("Chatbot caido", "")).andExpect(status().isCreated());
		createTicket(analyst(), siteBody("{\"kind\":\"VEHICLE_BARRIER\",\"elementName\":\"Entrada\","
				+ "\"manualSiteName\":\"Sitio sin equipos\",\"manualLocation\":\"Via principal\"}", "No sube", ""))
				.andExpect(status().isCreated());
	}

	@Test
	void everyRoleCanListAndOpenATicketButOnlyEditorsHaveActions() throws Exception {
		UUID id = createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			Cookie session = as(roles.toArray(Role[]::new));
			get(session, "/tickets").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
			get(session, "/tickets/" + id).andExpect(status().isOk());
		}
		for (Cookie reader : readOnlyRoles()) {
			get(reader, "/tickets/" + id).andExpect(jsonPath("$.allowedActions.length()").value(0));
		}
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItems("CLOSE",
				"MILESTONE", "RELATE")));
		get(as(Role.ANALISTA, Role.GERENTE), "/tickets/" + id)
				.andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("CLOSE")));
	}

	@Test
	void anUnauthenticatedCallIsRejectedAndAnUnknownTicketIs404() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/tickets"))
				.andExpect(status().isUnauthorized());
		assertProblem(get(analyst(), "/tickets/" + UUID.randomUUID()), 404, "TICKET_NOT_FOUND");
	}

	@Test
	void filtersTheListByStateClassTypePriorityNetworkEquipmentResponsibleAndCondition() throws Exception {
		seedFour();
		assertThat(codes("")).hasSize(4);
		assertThat(codes("?state=OPEN")).hasSize(4);
		assertThat(codes("?state=CLOSED")).isEmpty();
		assertThat(codes("?elementClass=EQUIPMENT")).containsExactlyInAnyOrder("TICKET-9001", "TICKET-9002");
		assertThat(codes("?elementClass=CHATBOT_SERVICE")).containsExactly("TICKET-9003");
		assertThat(codes("?elementClass=SITE_ELEMENT")).containsExactly("TICKET-9004");
		assertThat(codes("?incidentType=WAIT_FOR_CONNECTION")).containsExactly("TICKET-9002");
		assertThat(codes("?priority=HIGH")).containsExactly("TICKET-9001");
		assertThat(codes("?priority=MEDIUM")).containsExactlyInAnyOrder("TICKET-9002", "TICKET-9003", "TICKET-9004");
		assertThat(codes("?network=KIA")).containsExactly("TICKET-9002");
		assertThat(codes("?network=JAC")).containsExactly("TICKET-9001");
		assertThat(codes("?network=NONE")).as("chatbot y elementos del sitio: Sin red")
				.containsExactlyInAnyOrder("TICKET-9003", "TICKET-9004");
		assertThat(codes("?equipmentId=" + KIA)).containsExactly("TICKET-9002");
		assertThat(codes("?responsibleId=" + secondAnalystId())).containsExactly("TICKET-9002");
		assertThat(codes("?responsibleId=" + userId(Role.ANALISTA))).hasSize(3);
		assertThat(codes("?condition=PENDING_VALIDATION")).hasSize(4);
		assertThat(codes("?condition=IN_ATTENTION")).isEmpty();
		assertThat(codes("?condition=RECOVERED_OBSERVING")).isEmpty();
	}

	@Test
	void theConditionFollowsTheMilestonesWithoutAddingStates() throws Exception {
		seedFour();
		UUID second = jdbc.queryForObject("SELECT id FROM inc_ticket WHERE ticket_number = 9002", UUID.class);
		UUID episode = jdbc.queryForObject("SELECT id FROM inc_episode WHERE ticket_id = ?", UUID.class, second);
		jdbc.update("UPDATE inc_milestone SET status = 'RECORDED', occurred_at = now() WHERE episode_id = ? AND kind IN ('T1','T2')",
				episode);
		assertThat(codes("?condition=IN_ATTENTION")).containsExactly("TICKET-9002");
		assertThat(codes("?condition=PENDING_VALIDATION")).doesNotContain("TICKET-9002");
		jdbc.update("UPDATE inc_milestone SET status = 'RECORDED', occurred_at = now() WHERE episode_id = ? AND kind = 'T3'",
				episode);
		assertThat(codes("?condition=RECOVERED_OBSERVING")).containsExactly("TICKET-9002");
		assertThat(codes("?condition=IN_ATTENTION")).isEmpty();
		get(analyst(), "/tickets/" + second).andExpect(jsonPath("$.summary.condition").value("RECOVERED_OBSERVING"))
				.andExpect(jsonPath("$.summary.state").value("OPEN"));
	}

	@Test
	void searchesByCodeAndByText() throws Exception {
		seedFour();
		assertThat(codes("?search=TICKET-9003")).containsExactly("TICKET-9003");
		assertThat(codes("?search=9004")).containsExactly("TICKET-9004");
		assertThat(codes("?search=chatbot")).containsExactly("TICKET-9003");
		assertThat(codes("?search=sube")).containsExactly("TICKET-9004");
		assertThat(codes("?search=_")).as("el comodin no es especial").isEmpty();
		assertThat(codes("?search=nada-parecido")).isEmpty();
	}

	@Test
	void paginatesTheListWithTheTotal() throws Exception {
		seedFour();
		get(analyst(), "/tickets?size=2&page=0&sort=code,asc").andExpect(jsonPath("$.total").value(4))
				.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.items[0].code").value("TICKET-9001"));
		get(analyst(), "/tickets?size=2&page=1&sort=code,asc").andExpect(jsonPath("$.items[0].code").value("TICKET-9003"))
				.andExpect(jsonPath("$.items[1].code").value("TICKET-9004"));
		get(analyst(), "/tickets?size=2&page=5&sort=code,asc").andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.total").value(4));
		assertProblem(get(analyst(), "/tickets?size=0"), 400, "VALIDATION_FAILED");
		assertProblem(get(analyst(), "/tickets?size=101"), 400, "VALIDATION_FAILED");
		assertProblem(get(analyst(), "/tickets?page=-1"), 400, "VALIDATION_FAILED");
	}

	@Test
	void theDefaultOrderIsTheLatestActivityFirst() throws Exception {
		seedFour();
		assertThat(codes("")).containsExactly("TICKET-9004", "TICKET-9003", "TICKET-9002", "TICKET-9001");
		jdbc.update("UPDATE inc_ticket SET last_activity_at = now() + interval '1 hour' WHERE ticket_number = 9001");
		assertThat(codes("").getFirst()).isEqualTo("TICKET-9001");
	}

	@Test
	void sortsByEachAllowedFieldInBothDirections() throws Exception {
		seedFour();
		assertThat(codes("?sort=code,asc")).containsExactly("TICKET-9001", "TICKET-9002", "TICKET-9003", "TICKET-9004");
		assertThat(codes("?sort=code,desc")).containsExactly("TICKET-9004", "TICKET-9003", "TICKET-9002", "TICKET-9001");
		assertThat(codes("?sort=priority,desc").getFirst()).as("Alta antes que Media").isEqualTo("TICKET-9001");
		assertThat(codes("?sort=priority,asc").getLast()).isEqualTo("TICKET-9001");
		assertThat(codes("?sort=network,asc")).as("Sin red va al final").startsWith("TICKET-9001", "TICKET-9002");
		assertThat(codes("?sort=network,asc").subList(2, 4)).containsExactlyInAnyOrder("TICKET-9003", "TICKET-9004");
		// Por la etiqueta en español: «Desconexión» < «Espera de conexión» < «Falla de elemento…» < «Falla del servicio…».
		assertThat(codes("?sort=type,asc")).containsExactly("TICKET-9001", "TICKET-9002", "TICKET-9004", "TICKET-9003");
		assertThat(codes("?sort=type,desc").getFirst()).isEqualTo("TICKET-9003");
		assertThat(codes("?sort=responsible,asc")).hasSize(4);
		assertThat(codes("?sort=responsible,desc").getFirst()).as("Segundo Analista").isEqualTo("TICKET-9002");
		assertThat(codes("?sort=state,asc")).hasSize(4);
		assertThat(codes("?sort=t0,asc")).containsExactly("TICKET-9001", "TICKET-9002", "TICKET-9003", "TICKET-9004");
		assertThat(codes("?sort=t0,desc").getFirst()).isEqualTo("TICKET-9004");
		assertThat(codes("?sort=lastActivityAt,asc").getFirst()).isEqualTo("TICKET-9001");
		assertThat(codes("?sort=code")).as("sin direccion es ascendente").startsWith("TICKET-9001");
	}

	@ParameterizedTest
	@ValueSource(strings = { "element", "elementLabel", "code,sideways", "ticket_number", "code;drop table inc_ticket" })
	void anUnknownSortIsA400(String sort) throws Exception {
		assertProblem(get(analyst(), "/tickets?sort=" + java.net.URLEncoder.encode(sort, "UTF-8")), 400,
				"VALIDATION_FAILED");
	}

	@Test
	void theElementLabelUsesTheCurrentNameWhileTheDetailKeepsTheLabelAtT0() throws Exception {
		UUID id = createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		jdbc.update("UPDATE asset_equipment SET name = 'QA010 JAC Punto Renombrado' WHERE id = ?", JAC);

		get(analyst(), "/tickets").andExpect(jsonPath("$.items[0].elementLabel").value("QA010 JAC Punto Renombrado"));
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.summary.elementLabel").value("QA010 JAC Punto Renombrado"))
				.andExpect(jsonPath("$.equipment.labelAtT0").value("QA010 JAC Punto"))
				.andExpect(jsonPath("$.equipment.currentName").value("QA010 JAC Punto Renombrado"))
				.andExpect(jsonPath("$.equipment.active").value(true));
	}

	@Test
	void theNetworkKeptAtT0SurvivesMovingTheEquipmentToAnotherNetwork() throws Exception {
		UUID id = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		jdbc.update("UPDATE asset_equipment SET network = 'JAC' WHERE id = ?", KIA);

		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.summary.network").value("KIA"));
		assertThat(codes("?network=KIA")).containsExactly("TICKET-9001");
		assertThat(codes("?network=JAC")).isEmpty();
	}

	@Test
	void aDeactivatedEquipmentStillShowsItsHistoricalTicket() throws Exception {
		UUID id = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		jdbc.update("UPDATE asset_equipment SET admin_status = 2 WHERE id = ?", KIA);
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.equipment.active").value(false))
				.andExpect(jsonPath("$.equipment.currentName").value("QA010 KIA Electrolinera"));
	}

	@Test
	void connectorsShowTheirCurrentPositionAndAllOfThemShowAsTheWholeEquipment() throws Exception {
		UUID c1 = Feature010Fixtures.connectorId(KIA, 1);
		UUID c2 = Feature010Fixtures.connectorId(KIA, 2);
		UUID id = idOf(createTicket(analyst(), equipmentBody(UUID.randomUUID(), KIA, List.of(c1, c2), "CONNECTOR_FAILURE",
				"Falla general", "")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementLabel").value("QA010 KIA Electrolinera")));
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.connectors.length()").value(2))
				.andExpect(jsonPath("$.connectors[0].positionAtT0").value(1))
				.andExpect(jsonPath("$.connectors[1].currentPosition").value(2));
	}

	@Test
	void twoOfThreeConnectorsAreListedByTheirPositions() throws Exception {
		jdbc.update("""
				INSERT INTO asset_connector (id, equipment_id, ordinal, connector_type_id, active, created_at, updated_at, version)
				VALUES (?, ?, 3, ?, TRUE, now(), now(), 0)
				""", Feature010Fixtures.connectorId(KIA, 3), KIA, Feature010Fixtures.TYPE2);
		UUID c1 = Feature010Fixtures.connectorId(KIA, 1);
		UUID c3 = Feature010Fixtures.connectorId(KIA, 3);
		createTicket(analyst(), equipmentBody(UUID.randomUUID(), KIA, List.of(c3, c1), "CONNECTOR_FAILURE", "x", ""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementLabel").value("QA010 KIA Electrolinera / Conectores 1 y 3"));
	}

	@Test
	void aTicketBeforeTheStartNumberIsReportedAsFromThePreviousSequence() throws Exception {
		createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		get(analyst(), "/tickets?search=TICKET-085").andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.notice").value("TICKET_BEFORE_START"));
		get(analyst(), "/tickets?search=TICKET-9001").andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.notice").doesNotExist());
		get(analyst(), "/tickets?search=TICKET-9500").andExpect(jsonPath("$.notice").doesNotExist());
		get(analyst(), "/tickets?search=desconexion").andExpect(jsonPath("$.notice").doesNotExist());
		get(analyst(), "/tickets").andExpect(jsonPath("$.notice").doesNotExist());
	}

	@Test
	void commonFailureRelationsAreVisibleFromBothTicketsAndDoNotMergeThem() throws Exception {
		UUID a = createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		UUID b = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		UUID c = createEquipmentTicket(analyst(), Feature010Fixtures.SITE_REFERENCE, "DISCONNECTION");

		post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a), b, "COMMON_FAILURE", "Enlace del sitio"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.relations.length()").value(1))
				.andExpect(jsonPath("$.relations[0].ticketId").value(b.toString()))
				.andExpect(jsonPath("$.relations[0].code").value("TICKET-9002"))
				.andExpect(jsonPath("$.relations[0].kind").value("COMMON_FAILURE"))
				.andExpect(jsonPath("$.timeline[?(@.type=='RELATION_ADDED')].details.relatedCode")
						.value(org.hamcrest.Matchers.hasItem("TICKET-9002")));
		post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a), c, "COMMON_FAILURE", null))
				.andExpect(status().isOk()).andExpect(jsonPath("$.relations.length()").value(2));

		get(analyst(), "/tickets/" + b).andExpect(jsonPath("$.relations.length()").value(1))
				.andExpect(jsonPath("$.relations[0].ticketId").value(a.toString()))
				.andExpect(jsonPath("$.relations[0].code").value("TICKET-9001"))
				.andExpect(jsonPath("$.summary.state").value("OPEN"));
		assertThat(count("inc_ticket")).as("relacionar no fusiona tickets").isEqualTo(3);
		assertThat(count("inc_ticket_relation")).isEqualTo(2);
	}

	@Test
	void relationErrorsAreReportedWithStableCodes() throws Exception {
		UUID a = createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		UUID b = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");

		assertProblem(post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a), a, "COMMON_FAILURE", null)),
				422, "TICKET_RELATION_INVALID");
		assertProblem(post(analyst(), "/tickets/" + a + "/relations",
				relation(versionOf(a), UUID.randomUUID(), "COMMON_FAILURE", null)), 404, "TICKET_NOT_FOUND");
		assertProblem(post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a) + 7, b, "COMMON_FAILURE", null)),
				409, "TICKET_VERSION_CONFLICT");
		post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a), b, "INDEPENDENT_FAILURE", null))
				.andExpect(status().isOk());
		assertProblem(post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a), b, "COMMON_FAILURE", null)),
				409, "TICKET_RELATION_EXISTS");
		assertProblem(post(analyst(), "/tickets/" + b + "/relations", relation(versionOf(b), a, "COMMON_FAILURE", null)),
				409, "TICKET_RELATION_EXISTS");
		assertThat(count("inc_ticket_relation")).isEqualTo(1);
	}

	@Test
	void aRelationCanBeRemovedWithTheCurrentVersionAndReadersCannotRelate() throws Exception {
		UUID a = createEquipmentTicket(analyst(), JAC, "DISCONNECTION");
		UUID b = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		post(analyst(), "/tickets/" + a + "/relations", relation(versionOf(a), b, "COMMON_FAILURE", null))
				.andExpect(status().isOk());

		for (Cookie reader : readOnlyRoles()) {
			assertProblem(post(reader, "/tickets/" + a + "/relations", relation(versionOf(a), b, "COMMON_FAILURE", null)),
					403, "ACCESS_DENIED");
			assertProblem(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.delete(BASE + "/tickets/" + a + "/relations/" + b + "?version=" + versionOf(a)).cookie(reader)
					.with(com.tidsec.sarc_bakend.support.WebSessions.xsrf())), 403, "ACCESS_DENIED");
		}
		assertThat(count("inc_ticket_relation")).isEqualTo(1);

		assertProblem(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete(BASE + "/tickets/" + a + "/relations/" + b + "?version=" + (versionOf(a) + 3)).cookie(analyst())
				.with(com.tidsec.sarc_bakend.support.WebSessions.xsrf())), 409, "TICKET_VERSION_CONFLICT");
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete(BASE + "/tickets/" + a + "/relations/" + b + "?version=" + versionOf(a)).cookie(analyst())
				.with(com.tidsec.sarc_bakend.support.WebSessions.xsrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.relations.length()").value(0));
		assertThat(count("inc_ticket_relation")).isZero();
	}

	@Test
	void openMatchesListsTheOpenTicketsOfTheSameElementForEditorsOnly() throws Exception {
		UUID id = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		get(analyst(), "/tickets/open-matches?elementClass=EQUIPMENT&equipmentId=" + KIA)
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(id.toString())).andExpect(jsonPath("$[0].code").value("TICKET-9001"))
				.andExpect(jsonPath("$[0].incidentTypeLabel").value("Desconexión"));
		get(analyst(), "/tickets/open-matches?elementClass=EQUIPMENT&equipmentId=" + JAC).andExpect(jsonPath("$.length()").value(0));
		get(analyst(), "/tickets/open-matches?elementClass=CHATBOT_SERVICE").andExpect(jsonPath("$.length()").value(0));
		assertProblem(get(analyst(), "/tickets/open-matches?elementClass=EQUIPMENT"), 422, "TICKET_ELEMENT_INVALID");
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(get(reader, "/tickets/open-matches?elementClass=CHATBOT_SERVICE"), 403, "ACCESS_DENIED");
		}
	}

	@Test
	void theAssetSearchIsForEditorsAndOffersOnlyActiveEquipment() throws Exception {
		get(analyst(), "/assets?search=").andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name=='QA010 Inactivo')]").isEmpty())
				.andExpect(jsonPath("$[?(@.name=='QA010 Eliminado')]").isEmpty())
				.andExpect(jsonPath("$[?(@.name=='QA010 KIA Electrolinera')].connectors.length()")
						.value(org.hamcrest.Matchers.hasItem(2)));
		get(analyst(), "/assets?search=conectz").andExpect(jsonPath("$.length()").value(1));
		for (Cookie reader : readOnlyRoles()) {
			assertProblem(get(reader, "/assets"), 403, "ACCESS_DENIED");
		}
		assertProblem(get(analyst(), "/assets?limit=500"), 400, "VALIDATION_FAILED");
	}

	private static String relation(long version, UUID related, String kind, String reason) {
		return "{\"version\":%d,\"relatedTicketId\":\"%s\",\"kind\":\"%s\"%s}".formatted(version, related, kind,
				reason == null ? "" : ",\"reason\":\"" + reason + "\"");
	}
}
