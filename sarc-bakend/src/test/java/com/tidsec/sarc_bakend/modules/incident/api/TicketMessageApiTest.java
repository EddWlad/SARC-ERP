package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

import jakarta.servlet.http.Cookie;

/** Mensaje para WhatsApp (FR-055 a FR-060): solo lectura para los cinco roles, y copiar no deja rastro (FR-059). */
class TicketMessageApiTest extends IncidentApiSupport {

	private static final UUID KIA = Feature010Fixtures.KIA_STATION;

	private UUID ticket() throws Exception {
		return createEquipmentTicket(analyst(), KIA, "DISCONNECTION");
	}

	private String message(UUID id, String query) throws Exception {
		return get(analyst(), "/tickets/" + id + "/message" + query).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	@Test
	void theBaseTemplateCarriesTheCurrentNameLocationAndMapsOfTheEquipment() throws Exception {
		UUID id = ticket();
		get(analyst(), "/tickets/" + id + "/message").andExpect(status().isOk())
				.andExpect(jsonPath("$.ticketCode").value("TICKET-9001")).andExpect(jsonPath("$.state").value("OPEN"))
				.andExpect(jsonPath("$.missingFields.length()").value(0))
				.andExpect(jsonPath("$.text").value("""
						*TICKET-9001*
						Cargador/Electrolinera: QA010 KIA Electrolinera
						Ubicación: Via Prueba 2
						Google Maps: https://maps.example.test/kia-electrolinera
						Novedad: Sin comunicacion
						Estado: Abierto"""));
	}

	@Test
	void aTicketOfOneConnectorNamesItsPosition() throws Exception {
		UUID id = idOf(createTicket(analyst(), equipmentBody(UUID.randomUUID(), KIA,
				List.of(Feature010Fixtures.connectorId(KIA, 2)), "CONNECTOR_FAILURE", "Conector sin carga", ""))
				.andExpect(status().isCreated()));
		get(analyst(), "/tickets/" + id + "/message").andExpect(jsonPath("$.text")
				.value(containsString("Cargador/Electrolinera: QA010 KIA Electrolinera / Conector 2\n")));
	}

	@Test
	void everyRoleGetsTheMessageAndItCreatesNeitherEventsNorCommunications() throws Exception {
		UUID id = ticket();
		int events = count("inc_event");
		int communications = count("inc_communication");
		long version = versionOf(id);

		for (Cookie session : List.of(admin(), analyst(), readOnlyRoles().get(0), readOnlyRoles().get(1),
				readOnlyRoles().get(2))) {
			get(session, "/tickets/" + id + "/message?includeDetection=true&includeActions=true")
					.andExpect(status().isOk());
		}

		assertThat(count("inc_event")).isEqualTo(events);
		assertThat(count("inc_communication")).isEqualTo(communications);
		assertThat(versionOf(id)).as("copiar no cambia el ticket").isEqualTo(version);
	}

	@Test
	void withoutMapsTheLineStaysEmptyAndTheMissingFieldIsReportedWithoutFailing() throws Exception {
		UUID id = createEquipmentTicket(analyst(), Feature010Fixtures.NO_MAPS, "DISCONNECTION");
		get(analyst(), "/tickets/" + id + "/message").andExpect(status().isOk())
				.andExpect(jsonPath("$.missingFields").value(org.hamcrest.Matchers.hasItem("mapsUrl")))
				.andExpect(jsonPath("$.text").value(containsString("\nGoogle Maps: \n")));
	}

	@Test
	void theChatbotMessageHasNoLocationNorMapsAndShowsItsScope() throws Exception {
		UUID id = idOf(createTicket(analyst(), chatbotBody("Chatbot caido", ",\"chatbotScope\":\"PARTIAL\""))
				.andExpect(status().isCreated()));
		get(analyst(), "/tickets/" + id + "/message").andExpect(status().isOk())
				.andExpect(jsonPath("$.missingFields.length()").value(0))
				.andExpect(jsonPath("$.text").value("""
						*TICKET-9001*
						Elemento: Servicio de chatbot (alcance parcial)
						Novedad: Chatbot caido
						Estado: Abierto"""));
	}

	@Test
	void aBarrierKeepsTheLocationAndMapsOfT0EvenIfTheReferenceEquipmentChanges() throws Exception {
		String barrier = "{\"kind\":\"VEHICLE_BARRIER\",\"referenceEquipmentId\":\"%s\"}"
				.formatted(Feature010Fixtures.SITE_REFERENCE);
		UUID id = idOf(createTicket(analyst(), siteBody(barrier, "No sube", "")).andExpect(status().isCreated()));
		jdbc.update("UPDATE asset_equipment SET location = 'Otra ubicacion', maps_url = 'https://otro.example.test' WHERE id = ?",
				Feature010Fixtures.SITE_REFERENCE);

		get(analyst(), "/tickets/" + id + "/message").andExpect(jsonPath("$.text").value(containsString(
				"Elemento: QA010 CONECTZ Referencia / Barrera vehicular\nUbicación: " + Feature010Fixtures.SITE_NAME
						+ "\nGoogle Maps: " + Feature010Fixtures.SITE_MAPS_URL + "\n")));
	}

	@Test
	void theOptionsAddTheDetectionTimeAndOnlyTheActionsNotAnnulled() throws Exception {
		UUID id = ticket();
		post(analyst(), "/tickets/" + id + "/events", "{\"version\":%d,\"type\":\"CORRECTIVE_ACTION\",\"text\":\"Se reinicio el equipo\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		post(analyst(), "/tickets/" + id + "/events", "{\"version\":%d,\"type\":\"ESCALATED\",\"text\":\"Se escalo a tecnicos\",\"targetKind\":\"TECHNICAL\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		post(analyst(), "/tickets/" + id + "/events", "{\"version\":%d,\"type\":\"OBSERVATION\",\"text\":\"Solo una observacion\"}"
				.formatted(versionOf(id))).andExpect(status().isOk());
		UUID wrong = jdbc.queryForObject("SELECT id FROM inc_event WHERE text = 'Se escalo a tecnicos'", UUID.class);
		post(analyst(), "/tickets/" + id + "/events/" + wrong + "/annul",
				"{\"version\":%d,\"reason\":\"Registro equivocado\"}".formatted(versionOf(id))).andExpect(status().isOk());

		String off = message(id, "");
		assertThat(off).doesNotContain("Detección").doesNotContain("Acciones");

		String on = message(id, "?includeDetection=true&includeActions=true");
		assertThat(on).contains("Detección: ").contains("Acciones:").contains("Se reinicio el equipo")
				.doesNotContain("Se escalo a tecnicos").doesNotContain("Solo una observacion");

		get(analyst(), "/tickets/" + id + "/message?includeActions=true")
				.andExpect(jsonPath("$.text").value(not(containsString("Detección: "))));
	}

	@Test
	void withoutActionsTheOptionSaysSo() throws Exception {
		UUID id = ticket();
		assertThat(message(id, "?includeActions=true")).contains("Acciones: sin acciones registradas");
	}

	@Test
	void theMessageNeverCarriesSensitiveFieldsOrTheResponsible() throws Exception {
		UUID id = ticket();
		String text = message(id, "?includeDetection=true&includeActions=true").toLowerCase();
		assertThat(text).doesNotContain("uid").doesNotContain("puk").doesNotContain("ocpp").doesNotContain("prueba analista");
	}

	@Test
	void anUnknownTicketIs404AndAnAnonymousCallIs401() throws Exception {
		assertProblem(get(analyst(), "/tickets/" + UUID.randomUUID() + "/message"), 404, "TICKET_NOT_FOUND");
		assertProblem(mvc.perform(MockMvcRequestBuilders.get(BASE + "/tickets/" + UUID.randomUUID() + "/message")), 401,
				"UNAUTHENTICATED");
	}
}
