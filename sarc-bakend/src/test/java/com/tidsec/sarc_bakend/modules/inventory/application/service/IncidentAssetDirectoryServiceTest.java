package com.tidsec.sarc_bakend.modules.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;
import com.tidsec.sarc_bakend.shared.catalog.IncidentConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/** Puerto de activos para Incidencias sobre el inventario real (feature 010, research R1). */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class IncidentAssetDirectoryServiceTest {

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	IncidentAssetDirectory directory;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		jdbc.execute("TRUNCATE asset_equipment CASCADE");
		Feature010Fixtures.seedInventory(jdbc);
	}

	@Test
	void searchesByNameAndByCodeIgnoringCaseAndAccents() {
		assertThat(directory.search("jac punto", 10)).extracting(IncidentAsset::id)
				.containsExactly(Feature010Fixtures.JAC_POINT);
		assertThat(directory.search("KIA ELECTROLINERA", 10)).extracting(IncidentAsset::id)
				.containsExactly(Feature010Fixtures.KIA_STATION);
		assertThat(directory.search(Feature010Fixtures.KIA_STATION_CODE, 10)).extracting(IncidentAsset::id)
				.containsExactly(Feature010Fixtures.KIA_STATION);
		jdbc.update("UPDATE asset_equipment SET name = 'QA010 Estación Ñandú' WHERE id = ?", Feature010Fixtures.NO_MAPS);
		assertThat(directory.search("estacion nandu", 10)).extracting(IncidentAsset::id)
				.containsExactly(Feature010Fixtures.NO_MAPS);
	}

	@Test
	void searchNeverOffersInactiveOrDeletedEquipmentAndHonoursTheLimit() {
		List<UUID> found = directory.search("", 50).stream().map(IncidentAsset::id).toList();
		assertThat(found).contains(Feature010Fixtures.JAC_POINT, Feature010Fixtures.KIA_STATION,
				Feature010Fixtures.NO_MAPS, Feature010Fixtures.SITE_REFERENCE, Feature010Fixtures.RETIRED_CONNECTOR)
				.doesNotContain(Feature010Fixtures.INACTIVE, Feature010Fixtures.DELETED);
		assertThat(directory.search("", 2)).hasSize(2);
		assertThat(directory.search(null, 1)).hasSize(1);
		assertThat(directory.search("", 0)).isEmpty();
	}

	@Test
	void searchResultsAreSortedByName() {
		assertThat(directory.search("", 50)).extracting(IncidentAsset::name)
				.isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
	}

	@Test
	void findByIdsIncludesInactiveAndDeletedEquipmentBecauseTheHistoryBelongsToThem() {
		Map<UUID, IncidentAsset> found = directory.findByIds(Set.of(Feature010Fixtures.INACTIVE,
				Feature010Fixtures.DELETED, Feature010Fixtures.JAC_POINT, UUID.randomUUID()));
		assertThat(found).containsOnlyKeys(Feature010Fixtures.INACTIVE, Feature010Fixtures.DELETED,
				Feature010Fixtures.JAC_POINT);
		assertThat(found.get(Feature010Fixtures.INACTIVE).adminStatus()).isEqualTo(AdministrativeStatus.INACTIVE);
		assertThat(found.get(Feature010Fixtures.INACTIVE).active()).isFalse();
		assertThat(found.get(Feature010Fixtures.DELETED).adminStatus()).isEqualTo(AdministrativeStatus.DELETED);
		assertThat(found.get(Feature010Fixtures.JAC_POINT).active()).isTrue();
		assertThat(directory.findByIds(Set.of())).isEmpty();
	}

	@Test
	void exposesConnectorsWithTheirPositionAndRetiredOnesMarkedInactive() {
		IncidentAsset station = directory.findByIds(Set.of(Feature010Fixtures.RETIRED_CONNECTOR))
				.get(Feature010Fixtures.RETIRED_CONNECTOR);
		assertThat(station.connectors()).extracting(IncidentConnector::position, IncidentConnector::active)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(1, true),
						org.assertj.core.groups.Tuple.tuple(2, false));
		assertThat(station.connectors()).extracting(IncidentConnector::id).containsExactly(
				Feature010Fixtures.connectorId(Feature010Fixtures.RETIRED_CONNECTOR, 1),
				Feature010Fixtures.connectorId(Feature010Fixtures.RETIRED_CONNECTOR, 2));
		assertThat(station.connectors()).allSatisfy(connector -> assertThat(connector.typeLabel()).isNotBlank());

		IncidentAsset kia = directory.findByIds(Set.of(Feature010Fixtures.KIA_STATION))
				.get(Feature010Fixtures.KIA_STATION);
		assertThat(kia.connectors()).hasSize(2).allMatch(IncidentConnector::active);
	}

	@Test
	void carriesNetworkTypeLocationAndMapsAndLeavesMissingOnesNull() {
		IncidentAsset jac = directory.findByIds(Set.of(Feature010Fixtures.JAC_POINT))
				.get(Feature010Fixtures.JAC_POINT);
		assertThat(jac.network()).isEqualTo(ChargingNetwork.JAC);
		assertThat(jac.infrastructureType()).isEqualTo(InfrastructureType.CHARGING_POINT);
		assertThat(jac.location()).isEqualTo("Via Prueba 1");
		assertThat(jac.mapsUrl()).isEqualTo("https://maps.example.test/jac-punto");
		assertThat(jac.assignedCode()).isEqualTo(Feature010Fixtures.JAC_POINT_CODE);

		IncidentAsset noMaps = directory.findByIds(Set.of(Feature010Fixtures.NO_MAPS))
				.get(Feature010Fixtures.NO_MAPS);
		assertThat(noMaps.location()).isNull();
		assertThat(noMaps.mapsUrl()).isNull();
	}

	@Test
	void theRecordNeverCarriesUidPukOcppOrSerial() {
		assertThat(IncidentAsset.class.getRecordComponents()).extracting(component -> component.getName())
				.containsExactly("id", "name", "assignedCode", "network", "infrastructureType", "location", "mapsUrl",
						"adminStatus", "connectors")
				.doesNotContain("uidPuk", "ocppUrl", "serial");
		assertThat(IncidentConnector.class.getRecordComponents()).extracting(component -> component.getName())
				.containsExactly("id", "position", "typeLabel", "active");
	}
}
