package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/**
 * Tickets sin cargador (US3): el chatbot y los elementos del sitio no inventan un equipo, conectores ni red, no tienen
 * referencia de tiempo de un cargador y siguen mostrandose si el equipo de referencia deja de estar activo. Los avisos
 * de duplicado y la creacion basica de las tres variantes tambien se prueban en {@code TicketCreationApiTest}.
 */
class NonChargerTicketApiTest extends IncidentApiSupport {

	private static final String REFERENCED_BARRIER = "{\"kind\":\"VEHICLE_BARRIER\",\"referenceEquipmentId\":\"%s\"}"
			.formatted(Feature010Fixtures.SITE_REFERENCE);
	private static final String MANUAL_OTHER = "{\"kind\":\"OTHER\",\"elementName\":\"Porton peatonal\",\"manualSiteName\":\""
			+ Feature010Fixtures.SITE_WITHOUT_EQUIPMENT + "\",\"manualLocation\":\"Acceso norte\"}";

	private ResultActions create(String body) throws Exception {
		return createTicket(analyst(), body);
	}

	private UUID created(ResultActions result) throws Exception {
		return idOf(result.andExpect(status().isCreated()));
	}

	@Test
	void theThreeVariantsAreCreatedWithoutAnInventedEquipmentConnectorsOrNetwork() throws Exception {
		UUID chatbot = created(create(chatbotBody("Chatbot caido", ",\"chatbotScope\":\"PARTIAL\"")));
		UUID barrier = created(create(siteBody(REFERENCED_BARRIER, "No sube", "")));
		UUID other = created(create(siteBody(MANUAL_OTHER, "No abre", "")));

		for (UUID id : new UUID[] { chatbot, barrier, other }) {
			get(analyst(), "/tickets/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.equipment").doesNotExist())
					.andExpect(jsonPath("$.connectors.length()").value(0))
					.andExpect(jsonPath("$.summary.network").doesNotExist());
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_ticket WHERE equipment_id IS NOT NULL", Integer.class))
				.isZero();
		get(analyst(), "/tickets/" + chatbot).andExpect(jsonPath("$.chatbotScope").value("PARTIAL"))
				.andExpect(jsonPath("$.location").doesNotExist()).andExpect(jsonPath("$.mapsUrl").doesNotExist());
	}

	@Test
	void theLabelsAreTheChatbotServiceAndSiteSlashElement() throws Exception {
		UUID chatbot = created(create(chatbotBody("Chatbot caido", "")));
		UUID barrier = created(create(siteBody(REFERENCED_BARRIER, "No sube", "")));
		UUID other = created(create(siteBody(MANUAL_OTHER, "No abre", "")));

		get(analyst(), "/tickets/" + chatbot).andExpect(jsonPath("$.summary.elementLabel").value("Servicio de chatbot"));
		get(analyst(), "/tickets/" + barrier)
				.andExpect(jsonPath("$.summary.elementLabel").value("QA010 CONECTZ Referencia / Barrera vehicular"));
		get(analyst(), "/tickets/" + other).andExpect(jsonPath("$.summary.elementLabel")
				.value(Feature010Fixtures.SITE_WITHOUT_EQUIPMENT + " / Porton peatonal"));
	}

	@Test
	void aManualSiteWithoutMapsCreatesWithoutErrorAndShowsNoMapsLink() throws Exception {
		UUID other = created(create(siteBody(MANUAL_OTHER, "No abre", "")));
		get(analyst(), "/tickets/" + other).andExpect(jsonPath("$.location").value("Acceso norte"))
				.andExpect(jsonPath("$.mapsUrl").doesNotExist())
				.andExpect(jsonPath("$.siteElement.siteName").value(Feature010Fixtures.SITE_WITHOUT_EQUIPMENT));
	}

	@Test
	void theyAreGroupedAsNoNetworkInTheListFilter() throws Exception {
		created(create(chatbotBody("Chatbot caido", "")));
		created(create(siteBody(REFERENCED_BARRIER, "No sube", "")));
		created(createTicket(analyst(), equipmentBody(Feature010Fixtures.KIA_STATION, "DISCONNECTION", "Sin comunicacion")));

		get(analyst(), "/tickets?network=NONE").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
		get(analyst(), "/tickets?network=KIA").andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void theTypesMustMatchTheElementClass() throws Exception {
		String chatbotWithEquipmentType = "{\"clientRequestId\":\"%s\",\"elementClass\":\"CHATBOT_SERVICE\",\"incidentType\":\"DISCONNECTION\",\"description\":\"x\"}"
				.formatted(UUID.randomUUID());
		String siteWithEquipmentType = siteBody(REFERENCED_BARRIER, "x", "").replace("SITE_ELEMENT_FAILURE", "DISCONNECTION");
		String equipmentWithChatbotType = equipmentBody(Feature010Fixtures.KIA_STATION, "CHATBOT_SERVICE_FAILURE", "x");
		String equipmentWithSiteType = equipmentBody(Feature010Fixtures.KIA_STATION, "SITE_ELEMENT_FAILURE", "x");

		assertProblem(create(chatbotWithEquipmentType), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(siteWithEquipmentType), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(equipmentWithChatbotType), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(equipmentWithSiteType), 422, "TICKET_ELEMENT_INVALID");
		assertThat(count("inc_ticket")).isZero();
	}

	@Test
	void theyHaveNoChargerTimeReferenceAndNoFirstResponseOfChatbotQueries() throws Exception {
		UUID chatbot = created(create(chatbotBody("Chatbot caido", "")));
		UUID barrier = created(create(siteBody(REFERENCED_BARRIER, "No sube", "")));

		for (UUID id : new UUID[] { chatbot, barrier }) {
			get(analyst(), "/tickets/" + id).andExpect(jsonPath("$.episodes[0].references.length()").value(0))
					.andExpect(jsonPath("$.episodes[0].milestones.length()").value(5));
		}
	}

	@Test
	void aBarrierKeepsShowingAfterItsReferenceEquipmentIsDeactivatedButANewOneIsRefused() throws Exception {
		UUID barrier = created(create(siteBody(REFERENCED_BARRIER, "No sube", "")));
		jdbc.update("UPDATE asset_equipment SET admin_status = 2 WHERE id = ?", Feature010Fixtures.SITE_REFERENCE);

		get(analyst(), "/tickets/" + barrier).andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.state").value("OPEN"))
				.andExpect(jsonPath("$.location").value(Feature010Fixtures.SITE_NAME))
				.andExpect(jsonPath("$.mapsUrl").value(Feature010Fixtures.SITE_MAPS_URL));
		get(analyst(), "/tickets?elementClass=SITE_ELEMENT").andExpect(jsonPath("$.total").value(1));

		assertProblem(create(siteBody(REFERENCED_BARRIER.replace("VEHICLE_BARRIER", "OTHER").replace("}",
				",\"elementName\":\"Otra\"}"), "x", "")), 422, "TICKET_ELEMENT_INVALID");
		assertProblem(create(siteBody(REFERENCED_BARRIER, "otra falla", ",\"duplicateOverrideReason\":\"motivo\"")), 422,
				"TICKET_ELEMENT_INVALID");
	}

	@Test
	void theReadOnlyRolesSeeThemButCannotCreateThem() throws Exception {
		UUID chatbot = created(create(chatbotBody("Chatbot caido", "")));
		for (var reader : readOnlyRoles()) {
			get(reader, "/tickets/" + chatbot).andExpect(status().isOk())
					.andExpect(jsonPath("$.summary.elementLabel").value("Servicio de chatbot"))
					.andExpect(jsonPath("$.allowedActions.length()").value(0));
			assertProblem(createTicket(reader, siteBody(REFERENCED_BARRIER, "x", "")), 403, "ACCESS_DENIED");
		}
	}
}
