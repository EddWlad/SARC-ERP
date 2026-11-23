package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

import jakarta.servlet.http.Cookie;

/** Crear un ticket (FR-010 a FR-019; escenarios 1 a 9 de quickstart.md). */
class TicketCreationApiTest extends IncidentApiSupport {

	private static final UUID KIA = Feature010Fixtures.KIA_STATION;

	private ResultActions create(String body) throws Exception {
		return createTicket(analyst(), body);
	}

	@Test
	void threeFieldsCreateAnOpenTicketWithTheProposedPriorityOriginAndT0() throws Exception {
		Instant before = Instant.now().minusSeconds(2);
		ResultActions result = create(equipmentBody(Feature010Fixtures.JAC_POINT, "DISCONNECTION", "Sin comunicacion"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.code").value("TICKET-9001"))
				.andExpect(jsonPath("$.summary.state").value("OPEN"))
				.andExpect(jsonPath("$.summary.priority").value("HIGH"))
				.andExpect(jsonPath("$.summary.elementClass").value("EQUIPMENT"))
				.andExpect(jsonPath("$.reportOrigin").value("PLATFORM"))
				.andExpect(jsonPath("$.referenceVersion.code").value("PROTOCOLO-FIRMADO-1"))
				.andExpect(jsonPath("$.episodes.length()").value(1))
				.andExpect(jsonPath("$.episodes[0].milestones[0].kind").value("T0"))
				.andExpect(jsonPath("$.episodes[0].milestones[0].status").value("RECORDED"))
				.andExpect(jsonPath("$.episodes[0].milestones[1].status").value("PENDING"))
				.andExpect(jsonPath("$.episodes[0].milestones[4].kind").value("T4"))
				.andExpect(jsonPath("$.episodes[0].milestones[4].status").value("PENDING"))
				.andExpect(jsonPath("$.summary.condition").value("PENDING_VALIDATION"));
		Instant t0 = Instant.parse(com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(),
				"$.summary.t0"));
		assertThat(t0).isBetween(before, Instant.now().plusSeconds(2));
		assertThat(Duration.between(t0, Instant.now())).isLessThan(Duration.ofMinutes(1));
	}

	@Test
	void theInitialResponsibleIsTheAuthorAndTheNameShowsInTheListAndTheDetail() throws Exception {
		UUID id = idOf(create(equipmentBody(Feature010Fixtures.JAC_POINT, "DISCONNECTION", "x"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.summary.responsible.id")
						.value(userId(Role.ANALISTA, Role.GERENTE).equals(userId(Role.ADMINISTRADOR)) ? "" : userId(Role.ANALISTA).toString())));
		String name = "Prueba ANALISTA";
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.summary.responsible.name").value(name));
		get(analyst(), "/tickets").andExpect(jsonPath("$.items[0].responsible.name").value(name));
		assertThat(jdbc.queryForObject("SELECT current_responsible_name FROM inc_ticket", String.class)).isEqualTo(name);
	}

	@Test
	void aWholeEquipmentAndASingleConnectorAreOneTicketEach() throws Exception {
		create(equipmentBody(KIA, "CONNECTOR_FAILURE", "x")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementLabel").value("QA010 KIA Electrolinera"))
				.andExpect(jsonPath("$.connectors.length()").value(0));

		UUID connector2 = Feature010Fixtures.connectorId(Feature010Fixtures.SITE_REFERENCE, 2);
		create(equipmentBody(UUID.randomUUID(), Feature010Fixtures.SITE_REFERENCE, List.of(connector2),
				"CONNECTOR_FAILURE", "Conector 2", "")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementLabel").value("QA010 CONECTZ Referencia / Conector 2"))
				.andExpect(jsonPath("$.connectors.length()").value(1))
				.andExpect(jsonPath("$.connectors[0].positionAtT0").value(2));
		assertThat(count("inc_ticket")).isEqualTo(2);
		assertThat(count("inc_ticket_connector")).isEqualTo(1);
	}

	@Test
	void unconfiguredNumberingBlocksCreationAndConsumesNothing() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		assertProblem(create(equipmentBody(KIA, "DISCONNECTION", "x")), 409, "TICKET_NUMBERING_NOT_CONFIGURED");
		assertThat(count("inc_ticket")).isZero();
	}

	@Test
	void aNumberingFromAnotherEnvironmentBlocksCreation() throws Exception {
		Feature010Fixtures.configureNumbering(jdbc, 86, "PRODUCTION");
		assertProblem(create(equipmentBody(KIA, "DISCONNECTION", "x")), 409, "TICKET_NUMBERING_ENVIRONMENT_MISMATCH");
		assertThat(count("inc_ticket")).isZero();
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class)).isEqualTo(86);
	}

	@Test
	void anOpenTicketOfTheSameEquipmentWarnsAndCanBeOverriddenWithAnAuditedReason() throws Exception {
		UUID first = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");

		assertProblem(create(equipmentBody(KIA, "CONNECTOR_FAILURE", "otra falla")), 409, "TICKET_OPEN_DUPLICATE")
				.andExpect(jsonPath("$.openTickets.length()").value(1))
				.andExpect(jsonPath("$.openTickets[0].code").value("TICKET-9001"))
				.andExpect(jsonPath("$.openTickets[0].id").value(first.toString()));
		assertThat(count("inc_ticket")).as("el aviso no crea nada").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class))
				.as("ni consume un numero").isEqualTo(9002);

		create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "CONNECTOR_FAILURE", "otra falla",
				",\"duplicateOverrideReason\":\"Es una falla distinta\"")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.code").value("TICKET-9002"));
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_event WHERE action = 'TICKET_CREATED'
				   AND details ->> 'duplicateOverride' = 'true'
				   AND details ->> 'duplicateOverrideReason' = 'Es una falla distinta'
				""", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT duplicate_override_reason FROM inc_ticket WHERE ticket_number = 9002",
				String.class)).isEqualTo("Es una falla distinta");
	}

	@Test
	void theChatbotAndASameNamedBarrierWarnButADifferentBarrierDoesNot() throws Exception {
		create(chatbotBody("Chatbot caido", "")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementLabel").value("Servicio de chatbot"))
				.andExpect(jsonPath("$.summary.network").doesNotExist());
		assertProblem(create(chatbotBody("Sigue caido", "")), 409, "TICKET_OPEN_DUPLICATE");

		String entrance = "{\"kind\":\"VEHICLE_BARRIER\",\"elementName\":\"Entrada\",\"manualSiteName\":\"Sitio sin equipos\",\"manualLocation\":\"Via principal\"}";
		create(siteBody(entrance, "No sube", "")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementLabel").value("Sitio sin equipos / Entrada"));
		assertProblem(create(siteBody(entrance.replace("Entrada", " entrada "), "No sube", "")), 409,
				"TICKET_OPEN_DUPLICATE");
		create(siteBody(entrance.replace("Entrada", "Salida"), "No sube", "")).andExpect(status().isCreated());
	}

	@Test
	void aBarrierWithAReferenceEquipmentTakesLocationAndMapsAndHasNoNetworkOrConnectors() throws Exception {
		String barrier = "{\"kind\":\"VEHICLE_BARRIER\",\"referenceEquipmentId\":\"%s\"}"
				.formatted(Feature010Fixtures.SITE_REFERENCE);
		create(siteBody(barrier, "No sube", "")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.elementClass").value("SITE_ELEMENT"))
				.andExpect(jsonPath("$.summary.elementLabel").value("QA010 CONECTZ Referencia / Barrera vehicular"))
				.andExpect(jsonPath("$.summary.network").doesNotExist())
				.andExpect(jsonPath("$.connectors.length()").value(0))
				.andExpect(jsonPath("$.equipment").doesNotExist())
				.andExpect(jsonPath("$.location").value(Feature010Fixtures.SITE_NAME))
				.andExpect(jsonPath("$.mapsUrl").value(Feature010Fixtures.SITE_MAPS_URL));
		assertThat(count("asset_equipment")).as("no crea equipos ficticios").isEqualTo(7);
	}

	@Test
	void theSameClientRequestReturnsTheSameTicketWithoutConsumingANumber() throws Exception {
		UUID request = UUID.randomUUID();
		String body = equipmentBody(request, KIA, List.of(), "DISCONNECTION", "x", "");
		UUID first = idOf(create(body).andExpect(status().isCreated()));
		UUID again = idOf(create(body).andExpect(status().isOk()).andExpect(jsonPath("$.summary.code").value("TICKET-9001")));
		assertThat(again).isEqualTo(first);
		assertThat(count("inc_ticket")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class)).isEqualTo(9002);
	}

	@Test
	void anInvalidElementIsRejectedWith422AndNothingIsCreated() throws Exception {
		// Equipo inactivo, inexistente, conector ajeno, tipo que no corresponde y datos de otra clase.
		assertProblem(create(equipmentBody(Feature010Fixtures.INACTIVE, "DISCONNECTION", "x")), 422,
				"TICKET_ELEMENT_INVALID");
		assertProblem(create(equipmentBody(UUID.randomUUID(), "DISCONNECTION", "x")), 422, "TICKET_ELEMENT_INVALID");
		UUID foreign = Feature010Fixtures.connectorId(Feature010Fixtures.JAC_POINT, 1);
		assertProblem(create(equipmentBody(UUID.randomUUID(), KIA, List.of(foreign), "DISCONNECTION", "x", "")), 422,
				"TICKET_ELEMENT_INVALID");
		UUID retired = Feature010Fixtures.connectorId(Feature010Fixtures.RETIRED_CONNECTOR, 2);
		assertProblem(create(equipmentBody(UUID.randomUUID(), Feature010Fixtures.RETIRED_CONNECTOR, List.of(retired),
				"DISCONNECTION", "x", "")), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(equipmentBody(KIA, "CHATBOT_SERVICE_FAILURE", "x")), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(chatbotBody("x", ",\"equipmentId\":\"" + KIA + "\"")), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create("{\"clientRequestId\":\"%s\",\"elementClass\":\"CHATBOT_SERVICE\",\"incidentType\":\"DISCONNECTION\",\"description\":\"x\"}"
				.formatted(UUID.randomUUID())), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(siteBody("{\"kind\":\"OTHER\",\"manualSiteName\":\"Sitio\",\"manualLocation\":\"Via\"}", "x", "")),
				422, "TICKET_ELEMENT_INVALID");
		assertThat(count("inc_ticket")).isZero();
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class)).isEqualTo(9001);
	}

	@Test
	void aFutureOrUnjustifiedRetroactiveT0IsRejected() throws Exception {
		String future = ",\"t0\":\"" + Instant.now().plus(Duration.ofHours(2)) + "\"";
		assertProblem(create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "DISCONNECTION", "x", future)), 422,
				"TICKET_T0_INVALID");
		String past = ",\"t0\":\"" + Instant.now().minus(Duration.ofHours(2)) + "\"";
		assertProblem(create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "DISCONNECTION", "x", past)), 422,
				"TICKET_T0_INVALID");
		create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "DISCONNECTION", "x",
				past + ",\"t0Reason\":\"Avisaron por telefono\"")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.episodes[0].milestones[0].retroactive").value(true))
				.andExpect(jsonPath("$.episodes[0].milestones[0].reason").value("Avisaron por telefono"));
		assertThat(count("inc_ticket")).isEqualTo(1);
	}

	@Test
	void aMapsLinkThatIsNotHttpsIsRejected() throws Exception {
		String barrier = "{\"kind\":\"VEHICLE_BARRIER\",\"manualSiteName\":\"Sitio\",\"manualLocation\":\"Via\",\"manualMapsUrl\":\"http://maps.example.test/x\"}";
		assertProblem(create(siteBody(barrier, "x", "")), 422, "TICKET_MAPS_URL_INVALID");
		create(siteBody(barrier.replace("http://", "https://"), "x", "")).andExpect(status().isCreated());
	}

	@Test
	void changingTheProposedPriorityNeedsAReason() throws Exception {
		assertProblem(create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "DISCONNECTION", "x",
				",\"priority\":\"CRITICAL\"")), 422, "TICKET_PRIORITY_REASON_REQUIRED");
		create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "DISCONNECTION", "x",
				",\"priority\":\"CRITICAL\",\"priorityReason\":\"Es una electrolinera\"")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.summary.priority").value("CRITICAL"));
	}

	@Test
	void invalidInputIsA400() throws Exception {
		assertProblem(create(equipmentBody(KIA, "DISCONNECTION", "   ")), 400, "VALIDATION_FAILED");
		assertProblem(create(equipmentBody(KIA, "DISCONNECTION", "x".repeat(501))), 400, "VALIDATION_FAILED");
		assertProblem(create("{\"elementClass\":\"EQUIPMENT\",\"incidentType\":\"DISCONNECTION\",\"description\":\"x\"}"),
				400, "VALIDATION_FAILED");
		assertProblem(create(equipmentBody(UUID.randomUUID(), KIA, List.of(), "DISCONNECTION", "x",
				",\"reportOrigin\":\"OTHER\"")), 422, "TICKET_ELEMENT_INVALID");
		assertThat(count("inc_ticket")).isZero();
	}

	@Test
	void creationLeavesATicketCreatedEventForTheFutureBell() throws Exception {
		UUID id = createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
		get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.timeline.length()").value(1))
				.andExpect(jsonPath("$.timeline[0].type").value("TICKET_CREATED"));
		assertThat(jdbc.queryForObject("SELECT notifiable FROM inc_event WHERE ticket_id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void theReadOnlyRolesCannotCreateAndNothingChanges() throws Exception {
		for (Cookie session : readOnlyRoles()) {
			assertProblem(createTicket(session, equipmentBody(KIA, "DISCONNECTION", "x")), 403, "ACCESS_DENIED");
		}
		assertThat(count("inc_ticket")).isZero();
		assertThat(count("inc_event")).isZero();
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class)).isEqualTo(9001);
	}

	@Test
	void anAdministratorAndAnAnalystWithAnotherRoleCanCreate() throws Exception {
		createTicket(admin(), equipmentBody(KIA, "DISCONNECTION", "x")).andExpect(status().isCreated());
		createTicket(as(Role.ANALISTA, Role.GERENTE), equipmentBody(Feature010Fixtures.JAC_POINT, "DISCONNECTION", "x"))
				.andExpect(status().isCreated());
	}

	@Test
	void theResponseNeverCarriesUidPukOrOcpp() throws Exception {
		String json = create(equipmentBody(Feature010Fixtures.JAC_POINT, "DISCONNECTION", "x")).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		assertThat(json).doesNotContain("UID-").doesNotContain("uidPuk").doesNotContain("ocpp").doesNotContain("serial");
		get(analyst(), "/assets?search=jac").andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("UID-"))))
				.andExpect(jsonPath("$[0].connectors[0].typeLabel").exists());
	}
}
