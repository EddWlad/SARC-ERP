package com.tidsec.sarc_bakend.modules.incident.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Referencias de tiempo por version (FR-051a, P-03), con una segunda version de vigencia posterior. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class ReferenceCatalogTest {

	private static final UUID SECOND_VERSION = UUID.fromString("5b3f1c0e-0a10-4a01-9a10-0000000000f2");

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	ReferenceCatalog catalog;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void removeTestVersion() {
		jdbc.update("DELETE FROM inc_reference_rule WHERE version_id = ?", SECOND_VERSION);
		jdbc.update("DELETE FROM inc_reference_version WHERE id = ?", SECOND_VERSION);
	}

	@Test
	void theInitialVersionIsInForceForAnyT0() {
		ReferenceVersion version = catalog.versionAt(Instant.parse("2026-11-20T12:00:00Z"));
		assertThat(version.getCode()).isEqualTo("PROTOCOLO-FIRMADO-1");
		assertThat(version.getLabel()).isEqualTo("Protocolo de monitoreo firmado (versión por confirmar)");
		assertThat(catalog.versionAt(Instant.parse("2001-01-01T00:00:00Z")).getCode())
				.isEqualTo("PROTOCOLO-FIRMADO-1");
	}

	@Test
	void theRuleOfAnEpisodeComesFromTheVersionInForceAtItsT0() {
		insertSecondVersionFrom("2027-01-01T00:00:00Z");

		ReferenceVersion before = catalog.versionAt(Instant.parse("2026-12-31T23:59:59Z"));
		ReferenceVersion atChange = catalog.versionAt(Instant.parse("2027-01-01T00:00:00Z"));
		ReferenceVersion after = catalog.versionAt(Instant.parse("2027-06-01T00:00:00Z"));

		assertThat(before.getCode()).isEqualTo("PROTOCOLO-FIRMADO-1");
		assertThat(atChange.getCode()).isEqualTo("PROTOCOLO-FIRMADO-2");
		assertThat(after.getCode()).isEqualTo("PROTOCOLO-FIRMADO-2");

		assertThat(catalog.ruleFor(before, IncidentType.DISCONNECTION).orElseThrow().recoveryMaxMin())
				.contains(300);
		assertThat(catalog.ruleFor(after, IncidentType.DISCONNECTION).orElseThrow().recoveryMaxMin())
				.contains(240);
		assertThat(catalog.proposedPriority(before, IncidentType.DISCONNECTION)).isEqualTo(Priority.HIGH);
		assertThat(catalog.proposedPriority(after, IncidentType.DISCONNECTION)).isEqualTo(Priority.CRITICAL);
	}

	@Test
	void typesWithoutProtocolBackingHaveNoRuleAndProposeMedium() {
		ReferenceVersion version = catalog.versionAt(Instant.now());
		for (IncidentType type : new IncidentType[] { IncidentType.EMERGENCY_STOP,
				IncidentType.INSTALLATION_OR_CONNECTIVITY, IncidentType.CHATBOT_SERVICE_FAILURE,
				IncidentType.SITE_ELEMENT_FAILURE, IncidentType.UNCATALOGED }) {
			assertThat(catalog.ruleFor(version, type)).as(type.name()).isEmpty();
			assertThat(catalog.proposedPriority(version, type)).as(type.name()).isEqualTo(Priority.MEDIUM);
		}
	}

	@Test
	void theInitialRulesReproduceFr034AndFr051() {
		ReferenceVersion version = catalog.versionAt(Instant.now());
		ReferenceRule disconnection = catalog.ruleFor(version, IncidentType.DISCONNECTION).orElseThrow();
		assertThat(disconnection.validationMaxMin()).contains(5);
		assertThat(disconnection.recoveryMaxMin()).contains(300);
		assertThat(disconnection.isRecoveryExtendable()).isTrue();
		assertThat(catalog.ruleFor(version, IncidentType.CONNECTOR_FAILURE).orElseThrow().recoveryMaxMin())
				.contains(180);
		assertThat(catalog.ruleFor(version, IncidentType.CONNECTOR_STUCK).orElseThrow().recoveryMaxMin())
				.contains(120);
		assertThat(catalog.ruleFor(version, IncidentType.CANNOT_STOP_CHARGING).orElseThrow().recoveryMaxMin())
				.isEmpty();
		assertThat(catalog.ruleFor(version, IncidentType.WAIT_FOR_CONNECTION).orElseThrow().escalationMaxMin())
				.contains(60);
		assertThat(catalog.ruleFor(version, IncidentType.KIA_PERSISTENT_OUTAGE).orElseThrow().persistentAfterMin())
				.contains(30);
		assertThat(catalog.proposedPriority(version, IncidentType.CONNECTOR_STUCK)).isEqualTo(Priority.CRITICAL);
		assertThat(catalog.proposedPriority(version, IncidentType.WAIT_FOR_CONNECTION)).isEqualTo(Priority.MEDIUM);
	}

	@Test
	void aMissingVersionIsAnErrorBecauseTheSeedAlwaysCoversEveryT0() {
		assertThatThrownBy(() -> catalog.versionAt(Instant.parse("1990-01-01T00:00:00Z")))
				.isInstanceOf(IllegalStateException.class);
	}

	private void insertSecondVersionFrom(String effectiveFrom) {
		jdbc.update("""
				INSERT INTO inc_reference_version (id, code, label, effective_from, source_note)
				VALUES (?, 'PROTOCOLO-FIRMADO-2', 'Version de prueba', ?::timestamptz, 'Prueba')
				""", SECOND_VERSION, effectiveFrom);
		jdbc.update("""
				INSERT INTO inc_reference_rule (version_id, incident_type, validation_max_min, recovery_max_min,
				    recovery_extendable, proposed_priority)
				VALUES (?, 'DISCONNECTION', 5, 240, TRUE, 'CRITICAL')
				""", SECOND_VERSION);
	}
}
