package com.tidsec.sarc_bakend.modules.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class KiaPointDirectoryServiceTest {

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	KiaPointDirectory directory;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		jdbc.execute("TRUNCATE asset_equipment CASCADE");
		Feature008Fixtures.seedInventory(jdbc);
	}

	@Test
	void findsPointsByLiteralCodeIgnoringOuterSpacesOnly() {
		Map<String, List<KiaPointReference>> found = directory.findByAssignedCodes(
				List.of(" " + Feature008Fixtures.BLOCKED_CODE + " ", Feature008Fixtures.FREE_CODE,
						Feature008Fixtures.UNKNOWN_CODE, "", "90 0001"));

		assertThat(found).containsOnlyKeys(Feature008Fixtures.BLOCKED_CODE, Feature008Fixtures.FREE_CODE);
		assertThat(found.get(Feature008Fixtures.BLOCKED_CODE)).singleElement().satisfies(point -> {
			assertThat(point.id()).isEqualTo(Feature008Fixtures.BLOCKED);
			assertThat(point.inScope()).isTrue();
			assertThat(point.usageStatus()).isEqualTo("BLOCKED");
			assertThat(point.adminStatus()).isEqualTo(AdministrativeStatus.ACTIVE);
		});
	}

	@Test
	void reportsOutOfScopeEquipmentWithoutHidingIt() {
		Map<String, List<KiaPointReference>> found = directory.findByAssignedCodes(
				List.of("qa008-kia-station", "qa008-jac-point"));

		assertThat(found.get("qa008-kia-station")).singleElement()
				.satisfies(point -> assertThat(point.inScope()).isFalse());
		assertThat(found.get("qa008-jac-point")).singleElement().satisfies(point -> {
			assertThat(point.inScope()).isFalse();
			assertThat(point.usageStatus()).isEqualTo("FREE");
		});
	}

	@Test
	void returnsInactiveAndDeletedPointsWithTheirAdministrativeStatus() {
		Map<String, List<KiaPointReference>> found = directory.findByAssignedCodes(
				List.of(Feature008Fixtures.INACTIVE_CODE, Feature008Fixtures.DELETED_CODE));

		assertThat(found.get(Feature008Fixtures.INACTIVE_CODE)).singleElement()
				.satisfies(point -> assertThat(point.adminStatus()).isEqualTo(AdministrativeStatus.INACTIVE));
		assertThat(found.get(Feature008Fixtures.DELETED_CODE)).singleElement()
				.satisfies(point -> assertThat(point.adminStatus()).isEqualTo(AdministrativeStatus.DELETED));
	}

	@Test
	void aFreePointWithoutCodeIsFoundByIdButNeverByCode() {
		assertThat(directory.findByAssignedCodes(List.of(""))).isEmpty();
		assertThat(directory.findByIds(List.of(Feature008Fixtures.FREE_WITHOUT_CODE)).get(Feature008Fixtures.FREE_WITHOUT_CODE))
				.satisfies(point -> {
					assertThat(point.assignedCode()).isNull();
					assertThat(point.usageStatus()).isEqualTo("FREE");
					assertThat(point.inScope()).isTrue();
				});
		assertThat(directory.findByIds(List.of())).isEmpty();
		assertThat(directory.findByIds(List.of(UUID.randomUUID()))).isEmpty();
	}

	@Test
	void searchesKiaPointsByCodeOrNameWithoutCaseOrAccentsAndSkipsDeletedAndOutOfScope() {
		assertThat(directory.search("bloqueado", 20)).extracting(KiaPointReference::id)
				.containsExactlyInAnyOrder(Feature008Fixtures.BLOCKED, Feature008Fixtures.SECOND_BLOCKED);
		assertThat(directory.search("inactivo", 20)).extracting(KiaPointReference::id)
				.containsExactly(Feature008Fixtures.INACTIVE);
		assertThat(directory.search("QA008 PUNTO BLOQUEADO DOS", 20)).extracting(KiaPointReference::id)
				.containsExactly(Feature008Fixtures.SECOND_BLOCKED);
		assertThat(directory.search(Feature008Fixtures.FREE_CODE, 20)).extracting(KiaPointReference::id)
				.containsExactly(Feature008Fixtures.FREE);
		assertThat(directory.search("pruebas cda", 20)).extracting(KiaPointReference::id)
				.containsExactly(Feature008Fixtures.TEST_POINT);
		assertThat(directory.search("eliminado", 20)).isEmpty();
		assertThat(directory.search("electrolinera", 20)).isEmpty();
		assertThat(directory.search("jac", 20)).isEmpty();
	}

	@Test
	void searchHonoursTheLimitAndOrdersByName() {
		List<KiaPointReference> found = directory.search("", 3);

		assertThat(found).hasSize(3);
		assertThat(found).extracting(KiaPointReference::name).isSorted();
		assertThat(directory.search("", 0)).isEmpty();
	}

	@Test
	void inScopeListsActiveKiaPointsThatExistedAtThatInstant() {
		Instant now = Instant.now().plusSeconds(60);

		assertThat(directory.inScope(now)).extracting(KiaPointReference::id)
				.containsExactlyInAnyOrder(Feature008Fixtures.BLOCKED, Feature008Fixtures.SECOND_BLOCKED,
						Feature008Fixtures.FREE, Feature008Fixtures.FREE_WITHOUT_CODE,
						Feature008Fixtures.DISCONNECTED, Feature008Fixtures.TEST_POINT)
				.doesNotContain(Feature008Fixtures.INACTIVE, Feature008Fixtures.DELETED);
		assertThat(directory.inScope(Instant.parse("2000-01-01T00:00:00Z"))).isEmpty();
	}

	@Test
	void theDisconnectedConstantMatchesTheInventoryEnumAndTheReferenceFlagsIt() {
		assertThat(KiaPointReference.DISCONNECTED)
				.isEqualTo(com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus.DISCONNECTED.name());
		assertThat(directory.allKiaPoints().stream().filter(KiaPointReference::disconnected))
				.extracting(KiaPointReference::id).containsExactly(Feature008Fixtures.DISCONNECTED);
	}

	@Test
	void allKiaPointsIncludesInactiveDeletedAndDisconnectedOnesButNeverOtherNetworksOrStations() {
		assertThat(directory.allKiaPoints()).extracting(KiaPointReference::id)
				.containsExactlyInAnyOrder(Feature008Fixtures.BLOCKED, Feature008Fixtures.SECOND_BLOCKED,
						Feature008Fixtures.FREE, Feature008Fixtures.FREE_WITHOUT_CODE, Feature008Fixtures.DISCONNECTED,
						Feature008Fixtures.INACTIVE, Feature008Fixtures.DELETED, Feature008Fixtures.TEST_POINT)
				.doesNotContain(Feature008Fixtures.KIA_STATION, Feature008Fixtures.JAC_POINT);
		assertThat(directory.allKiaPoints()).allSatisfy(point -> assertThat(point.inScope()).isTrue());
		assertThat(directory.allKiaPoints()).allSatisfy(point -> assertThat(point.createdAt()).isNotNull());
		assertThat(directory.allKiaPoints()).extracting(KiaPointReference::name).isSorted();
	}
}
