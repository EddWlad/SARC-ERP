package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** FR-001 a FR-007: el elemento afectado se valida y se guarda como era en T0. */
class AffectedElementEquipmentTest {

	private static final UUID CONNECTOR_1 = UUID.randomUUID();
	private static final UUID CONNECTOR_2 = UUID.randomUUID();
	private static final UUID RETIRED = UUID.randomUUID();

	private static IncidentAsset station(AdministrativeStatus status) {
		return new IncidentAsset(UUID.randomUUID(), "KIA Norte", "910002", ChargingNetwork.KIA,
				InfrastructureType.CHARGING_STATION, "Via 1", "https://maps.example.test/x", status,
				List.of(new IncidentConnector(CONNECTOR_1, 1, "CCS2", true),
						new IncidentConnector(CONNECTOR_2, 2, "Tipo 2", true),
						new IncidentConnector(RETIRED, 3, "CCS2", false)));
	}

	private static void assertInvalid(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessRuleException.class,
				error -> assertThat(error.code()).isEqualTo(code));
	}

	@Test
	void aWholeEquipmentHasNoConnectors() {
		IncidentAsset asset = station(AdministrativeStatus.ACTIVE);
		AffectedElement element = AffectedElement.forEquipment(asset, List.of());
		assertThat(element.elementClass()).isEqualTo(ElementClass.EQUIPMENT);
		assertThat(element.connectors()).isEmpty();
		assertThat(AffectedElement.forEquipment(asset, null).connectors()).isEmpty();
	}

	@Test
	void keepsTheLabelNetworkAndInfrastructureAsTheyWereAtT0() {
		IncidentAsset asset = station(AdministrativeStatus.ACTIVE);
		AffectedElement element = AffectedElement.forEquipment(asset, List.of());
		assertThat(element.equipmentId()).isEqualTo(asset.id());
		assertThat(element.equipmentLabelAtT0()).isEqualTo("KIA Norte");
		assertThat(element.networkAtT0()).isEqualTo("KIA");
		assertThat(element.infrastructureTypeAtT0()).isEqualTo("CHARGING_STATION");
	}

	@Test
	void activeConnectorsOfTheSameEquipmentAreAccepted() {
		AffectedElement element = AffectedElement.forEquipment(station(AdministrativeStatus.ACTIVE),
				List.of(CONNECTOR_2, CONNECTOR_1));
		assertThat(element.connectors()).extracting(ConnectorRef::connectorId, ConnectorRef::positionAtT0)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(CONNECTOR_2, 2),
						org.assertj.core.groups.Tuple.tuple(CONNECTOR_1, 1));
	}

	@Test
	void aRepeatedConnectorCountsOnce() {
		assertThat(AffectedElement.forEquipment(station(AdministrativeStatus.ACTIVE),
				List.of(CONNECTOR_1, CONNECTOR_1)).connectors()).hasSize(1);
	}

	@Test
	void aForeignOrRetiredConnectorIsRejected() {
		IncidentAsset asset = station(AdministrativeStatus.ACTIVE);
		assertInvalid(() -> AffectedElement.forEquipment(asset, List.of(UUID.randomUUID())),
				IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forEquipment(asset, List.of(CONNECTOR_1, RETIRED)),
				IncidentErrors.ELEMENT_INVALID);
	}

	@Test
	void anInactiveDeletedOrMissingEquipmentIsRejected() {
		assertInvalid(() -> AffectedElement.forEquipment(station(AdministrativeStatus.INACTIVE), List.of()),
				IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forEquipment(station(AdministrativeStatus.DELETED), List.of()),
				IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forEquipment(null, List.of()), IncidentErrors.ELEMENT_INVALID);
	}

	@Test
	void theChatbotHasNoEquipmentNetworkOrLocation() {
		AffectedElement element = AffectedElement.chatbot(ChatbotScope.PARTIAL);
		assertThat(element.elementClass()).isEqualTo(ElementClass.CHATBOT_SERVICE);
		assertThat(element.equipmentId()).isNull();
		assertThat(element.networkAtT0()).isNull();
		assertThat(element.locationAtT0()).isNull();
		assertThat(element.connectors()).isEmpty();
		assertThat(AffectedElement.chatbot(null).chatbotScope()).isNull();
	}

	@Test
	void aSiteElementWithAReferenceTakesOnlyNameLocationAndMaps() {
		IncidentAsset reference = station(AdministrativeStatus.ACTIVE);
		AffectedElement element = AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, reference, null,
				null, null);
		assertThat(element.elementClass()).isEqualTo(ElementClass.SITE_ELEMENT);
		assertThat(element.referenceEquipmentId()).isEqualTo(reference.id());
		assertThat(element.siteLabelAtT0()).isEqualTo("KIA Norte");
		assertThat(element.locationAtT0()).isEqualTo("Via 1");
		assertThat(element.mapsUrlAtT0()).isEqualTo("https://maps.example.test/x");
		assertThat(element.networkAtT0()).as("no hereda la red del equipo de referencia").isNull();
		assertThat(element.connectors()).as("ni sus conectores").isEmpty();
		assertThat(element.equipmentId()).as("el elemento no es el equipo").isNull();
	}

	@Test
	void aSiteElementWithoutEquipmentsUsesAManualLocationWithOptionalMaps() {
		AffectedElement element = AffectedElement.forSiteElement(SiteElementKind.OTHER, " Porton ", null,
				"Sitio sin equipos", "Via principal", null);
		assertThat(element.manualSiteName()).isEqualTo("Sitio sin equipos");
		assertThat(element.elementName()).isEqualTo("Porton");
		assertThat(element.mapsUrlAtT0()).isNull();
		assertThat(AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, null, "Sitio", "Via",
				"https://maps.example.test/s").manualMapsUrl()).isEqualTo("https://maps.example.test/s");
	}

	@Test
	void aSiteElementNeedsExactlyOneLocationSource() {
		IncidentAsset reference = station(AdministrativeStatus.ACTIVE);
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, null, null, null, null),
				IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, null, "Sitio", null,
				null), IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, reference, "Sitio",
				"Via", null), IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null,
				station(AdministrativeStatus.INACTIVE), null, null, null), IncidentErrors.ELEMENT_INVALID);
	}

	@Test
	void otherSiteElementNeedsItsNameAndTheKindIsRequired() {
		IncidentAsset reference = station(AdministrativeStatus.ACTIVE);
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.OTHER, "  ", reference, null, null, null),
				IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forSiteElement(null, null, reference, null, null, null),
				IncidentErrors.ELEMENT_INVALID);
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.OTHER, "x".repeat(121), reference, null,
				null, null), IncidentErrors.ELEMENT_INVALID);
	}

	@Test
	void aManualMapsLinkMustBeHttps() {
		for (String bad : new String[] { "http://maps.example.test/s", "maps.example.test", "ftp://x", "javascript:alert(1)" }) {
			assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, null, "Sitio",
					"Via", bad), IncidentErrors.MAPS_URL_INVALID);
		}
		assertInvalid(() -> AffectedElement.forSiteElement(SiteElementKind.VEHICLE_BARRIER, null, null, "Sitio", "Via",
				"https://" + "a".repeat(500)), IncidentErrors.MAPS_URL_INVALID);
	}

	@Test
	void theDuplicateKeyOfAnEquipmentIsItsIdentityEvenWithConnectors() {
		IncidentAsset asset = station(AdministrativeStatus.ACTIVE);
		String whole = DuplicateKey.of(AffectedElement.forEquipment(asset, List.of()));
		String withConnector = DuplicateKey.of(AffectedElement.forEquipment(asset, List.of(CONNECTOR_2)));
		assertThat(whole).isEqualTo("EQ:" + asset.id()).isEqualTo(withConnector);
	}
}
