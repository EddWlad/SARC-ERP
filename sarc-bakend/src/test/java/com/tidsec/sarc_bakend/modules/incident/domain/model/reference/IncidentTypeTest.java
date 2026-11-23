package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/**
 * FR-022: la matriz de compatibilidad entre tipos y clases de elemento esta en el dominio y en el {@code CHECK} de
 * V021. Esta prueba comprueba, contra PostgreSQL, que las dos coinciden para cada combinacion.
 */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class IncidentTypeTest {

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM inc_ticket");
	}

	@Test
	void equipmentAdmitsEveryTypeExceptTheChatbotAndSiteElementFailures() {
		assertThat(allowed(ElementClass.EQUIPMENT)).containsExactlyInAnyOrderElementsOf(EnumSet.complementOf(
				EnumSet.of(IncidentType.CHATBOT_SERVICE_FAILURE, IncidentType.SITE_ELEMENT_FAILURE)));
	}

	@Test
	void chatbotAdmitsOnlyItsFailureAndTheUncataloguedType() {
		assertThat(allowed(ElementClass.CHATBOT_SERVICE)).containsExactlyInAnyOrder(
				IncidentType.CHATBOT_SERVICE_FAILURE, IncidentType.UNCATALOGED);
	}

	@Test
	void siteElementsAdmitOnlyTheirFailureAndTheUncataloguedType() {
		assertThat(allowed(ElementClass.SITE_ELEMENT)).containsExactlyInAnyOrder(
				IncidentType.SITE_ELEMENT_FAILURE, IncidentType.UNCATALOGED);
	}

	@Test
	void noEquipmentTypeIsOfferedToTheChatbotOrToASiteElement() {
		for (IncidentType type : EnumSet.complementOf(EnumSet.of(IncidentType.CHATBOT_SERVICE_FAILURE,
				IncidentType.SITE_ELEMENT_FAILURE, IncidentType.UNCATALOGED))) {
			assertThat(type.allowedFor(ElementClass.CHATBOT_SERVICE)).as(type.name()).isFalse();
			assertThat(type.allowedFor(ElementClass.SITE_ELEMENT)).as(type.name()).isFalse();
		}
	}

	@Test
	void theDomainMatrixMatchesTheCheckConstraintOfV021ForEveryCombination() {
		int checked = 0;
		for (ElementClass elementClass : ElementClass.values()) {
			for (IncidentType type : IncidentType.values()) {
				boolean databaseAccepts = databaseAccepts(elementClass, type);
				assertThat(databaseAccepts).as("%s con %s".formatted(type, elementClass))
						.isEqualTo(type.allowedFor(elementClass));
				checked++;
			}
		}
		assertThat(checked).isEqualTo(ElementClass.values().length * IncidentType.values().length);
	}

	@Test
	void theCatalogueHasTheTwelveTypesOfFr020() {
		assertThat(IncidentType.values()).hasSize(12);
	}

	private static Set<IncidentType> allowed(ElementClass elementClass) {
		Set<IncidentType> found = EnumSet.noneOf(IncidentType.class);
		for (IncidentType type : IncidentType.values()) {
			if (type.allowedFor(elementClass)) {
				found.add(type);
			}
		}
		return found;
	}

	private boolean databaseAccepts(ElementClass elementClass, IncidentType type) {
		Map<String, Object> columns = new LinkedHashMap<>();
		OffsetDateTime now = OffsetDateTime.now();
		columns.put("id", UUID.randomUUID());
		columns.put("ticket_number", (int) (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 900_000_000) + 1);
		columns.put("client_request_id", UUID.randomUUID());
		columns.put("state", "OPEN");
		columns.put("element_class", elementClass.name());
		columns.put("incident_type", type.name());
		columns.put("report_origin", "PLATFORM");
		columns.put("description", "Prueba de compatibilidad");
		columns.put("priority", "MEDIUM");
		columns.put("current_responsible_id", UUID.randomUUID());
		columns.put("current_responsible_name", "Analista de prueba");
		columns.put("duplicate_key", "K");
		columns.put("last_activity_at", now);
		columns.put("created_at", now);
		columns.put("updated_at", now);
		columns.put("version", 0L);
		switch (elementClass) {
			case EQUIPMENT -> {
				columns.put("equipment_id", UUID.randomUUID());
				columns.put("equipment_label_at_t0", "Equipo");
				columns.put("network_at_t0", "KIA");
				columns.put("infrastructure_type_at_t0", "CHARGING_POINT");
			}
			case SITE_ELEMENT -> {
				columns.put("site_element_kind", SiteElementKind.VEHICLE_BARRIER.name());
				columns.put("reference_equipment_id", UUID.randomUUID());
			}
			case CHATBOT_SERVICE -> {
			}
		}
		try {
			jdbc.update("INSERT INTO inc_ticket (" + String.join(", ", columns.keySet()) + ") VALUES ("
					+ String.join(", ", columns.keySet().stream().map(key -> "?").toList()) + ")",
					columns.values().toArray());
			return true;
		}
		catch (DataIntegrityViolationException rejected) {
			return false;
		}
	}
}
