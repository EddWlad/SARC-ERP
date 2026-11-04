package com.tidsec.sarc_bakend.modules.uptime.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.uptime.application.command.CorrectMeasurementCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.command.RegisterMeasurementCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementDetail;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementOrigin;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature005Fixtures;

/** Continuidad por posicion (D06, D07) y etiqueta de la fecha del hecho (D12). */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
@WithMockUser(roles = "ANALISTA")
class UptimeHistoryContinuityTest {

	private static final UUID EQUIPMENT = Feature005Fixtures.JAC_STATION;
	private static final UUID CONNECTOR = Feature005Fixtures.connectorId(EQUIPMENT, 1);

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	MeasurementService service;

	@Autowired
	MeasurementReadService reads;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		jdbc.execute("TRUNCATE upt_measurement_revision, upt_measurement, asset_equipment CASCADE");
		Feature005Fixtures.seedInventory(jdbc);
	}

	private MeasurementView register(String day) {
		OffsetDateTime start = OffsetDateTime.parse(day + "T00:00:00-05:00");
		return service.register(new RegisterMeasurementCommand(CONNECTOR, MeasurementSource.ENERLINK, start.toInstant(),
				start.plusDays(1).toInstant(), PeriodType.ROLLING_24H, false, BigDecimal.valueOf(1440), BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, null, MeasurementOrigin.MANUAL, null)).view();
	}

	private List<UUID> history() {
		return jdbc.queryForList("SELECT DISTINCT connector_id FROM upt_measurement", UUID.class);
	}

	@Test
	void changingConnectorTypeKeepsPositionHistory() {
		register("2026-10-01");
		jdbc.update("UPDATE asset_connector SET connector_type_id = ? WHERE id = ?", Feature005Fixtures.CCS2, CONNECTOR);
		register("2026-10-02");

		assertThat(history()).containsExactly(CONNECTOR);
		assertThat(jdbc.queryForList("SELECT connector_type_label FROM upt_measurement_revision"
				+ " ORDER BY entered_at", String.class)).containsExactly("Tipo 2 (Mennekes) AC", "CCS Combo 2 DC");
	}

	@Test
	void retiringAndRestoringPositionKeepsIdentity() {
		register("2026-10-01");
		jdbc.update("UPDATE asset_connector SET active = FALSE WHERE id = ?", CONNECTOR);
		jdbc.update("UPDATE asset_connector SET active = TRUE WHERE id = ?", CONNECTOR);
		register("2026-10-02");

		assertThat(history()).containsExactly(CONNECTOR);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement", Integer.class)).isEqualTo(2);
	}

	@Test
	void replacementKeepsMeasurementsOnReplacedEquipment() {
		register("2026-10-01");
		jdbc.update("UPDATE asset_equipment SET admin_status = 2 WHERE id = ?", EQUIPMENT);

		assertThat(jdbc.queryForObject("SELECT equipment_id FROM upt_measurement", UUID.class)).isEqualTo(EQUIPMENT);
	}

	@Test
	void correctionKeepsFactLabelAfterRename() {
		MeasurementView created = register("2026-10-01");
		jdbc.update("UPDATE asset_equipment SET name = 'Nombre nuevo' WHERE id = ?", EQUIPMENT);

		service.correct(created.measurement().getId(),
				new CorrectMeasurementCommand(created.measurement().getVersion(), ReadingClass.MEASURED,
						PeriodType.ROLLING_24H, false, BigDecimal.valueOf(1400), BigDecimal.valueOf(40), BigDecimal.ZERO,
						BigDecimal.ZERO, null, "Corregido"));

		assertThat(jdbc.queryForList("SELECT DISTINCT equipment_label FROM upt_measurement_revision", String.class))
				.containsExactly("QA005 JAC Estación");
	}

	@Test
	void detailDistinguishesFactLabelFromCurrentName() {
		MeasurementView created = register("2026-10-01");
		jdbc.update("UPDATE asset_equipment SET name = 'Nombre nuevo' WHERE id = ?", EQUIPMENT);

		MeasurementDetail detail = reads.detail(created.measurement().getId());

		assertThat(detail.connector().equipmentName()).isEqualTo("Nombre nuevo");
		assertThat(detail.view().revision().getEquipmentLabel()).isEqualTo("QA005 JAC Estación");
	}
}
