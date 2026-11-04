package com.tidsec.sarc_bakend.modules.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature005Fixtures;

@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class ConnectorDirectoryServiceTest {

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	ConnectorDirectory directory;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		jdbc.execute("TRUNCATE asset_equipment CASCADE");
		Feature005Fixtures.seedInventory(jdbc);
	}

	@Test
	void listsConnectorsOrderedByEquipmentAndPositionWithoutDeletedEquipment() {
		List<MeasurableConnector> found = directory.search(ConnectorQuery.all());

		assertThat(found).extracting(MeasurableConnector::equipmentId)
				.doesNotContain(Feature005Fixtures.DELETED_EQUIPMENT);
		List<MeasurableConnector> jac = directory.search(
				new ConnectorQuery(null, null, null, null, Feature005Fixtures.JAC_STATION));
		assertThat(jac).extracting(MeasurableConnector::ordinal).containsExactly(1, 2, 3);
	}

	@Test
	void includesInactivePositionsAndInactiveEquipment() {
		assertThat(directory.search(new ConnectorQuery(null, null, null, null, Feature005Fixtures.JAC_STATION)))
				.filteredOn(connector -> !connector.connectorActive()).extracting(MeasurableConnector::ordinal)
				.containsExactly(3);
		assertThat(directory.search(new ConnectorQuery(null, null, null, null, Feature005Fixtures.INACTIVE_EQUIPMENT)))
				.isNotEmpty().allSatisfy(connector -> assertThat(connector.equipmentStatus())
						.isEqualTo(AdministrativeStatus.INACTIVE));
	}

	@Test
	void filtersByNetworkTypeBrandAndText() {
		assertThat(directory.search(new ConnectorQuery(null, ChargingNetwork.KIA, null, null, null)))
				.isNotEmpty().allSatisfy(connector -> assertThat(connector.network()).isEqualTo(ChargingNetwork.KIA));
		assertThat(directory.search(new ConnectorQuery(null, null, InfrastructureType.CHARGING_POINT, null, null)))
				.isNotEmpty()
				.allSatisfy(connector -> assertThat(connector.type()).isEqualTo(InfrastructureType.CHARGING_POINT));
		assertThat(directory.search(new ConnectorQuery(null, null, null, "WALLBOX", null)))
				.isNotEmpty().allSatisfy(connector -> assertThat(connector.brand()).isEqualTo("WALLBOX"));
		assertThat(directory.search(new ConnectorQuery("qa005 jac estación", null, null, null, null)))
				.extracting(MeasurableConnector::equipmentId).containsOnly(Feature005Fixtures.JAC_STATION);
	}

	@Test
	void unknownBrandFindsNothing() {
		assertThat(directory.search(new ConnectorQuery(null, null, null, "OTRA", null))).isEmpty();
	}

	@Test
	void findsByIdIncludingDeletedEquipment() {
		UUID deleted = jdbc.queryForObject("SELECT id FROM asset_connector WHERE equipment_id = ? LIMIT 1",
				UUID.class, Feature005Fixtures.DELETED_EQUIPMENT);

		Map<UUID, MeasurableConnector> found = directory.findByIds(List.of(deleted));

		assertThat(found.get(deleted).equipmentStatus()).isEqualTo(AdministrativeStatus.DELETED);
		assertThat(directory.findByIds(List.of())).isEmpty();
	}
}
