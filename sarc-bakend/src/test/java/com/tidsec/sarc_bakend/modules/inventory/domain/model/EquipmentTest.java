package com.tidsec.sarc_bakend.modules.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

class EquipmentTest {

	private final ConnectorType ccs2 = ConnectorType.create("CCS2", "CCS Combo 2");
	private final ConnectorType type2 = ConnectorType.create("TYPE2", "Tipo 2");
	private final ConnectorType chademo = ConnectorType.create("CHADEMO", "CHAdeMO");

	private static EquipmentDetails details(String serial, String code, String uidPuk, UsageStatus usage) {
		return new EquipmentDetails(EquipmentBrand.WALLBOX, "Equipo " + serial, serial, code, uidPuk, null, null, null,
				usage);
	}

	private Equipment station() {
		return Equipment.register(ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
				details("st-1", null, null, null), List.of(ccs2, type2, chademo), null);
	}

	@Test
	void registersAKiaPointWithNormalizedIdentifiers() {
		Equipment point = Equipment.register(ChargingNetwork.KIA, InfrastructureType.CHARGING_POINT,
				details("  ab-123 ", "123456", " uid-9 ", UsageStatus.FREE), List.of(type2), null);

		assertThat(point.getSerial()).isEqualTo("AB-123");
		assertThat(point.getUidPuk()).isEqualTo("UID-9");
		assertThat(point.getAssignedCode()).isEqualTo("123456");
		assertThat(point.getAdminStatus()).isEqualTo(AdministrativeStatus.ACTIVE);
		assertThat(point.getScheduleMode()).isEqualTo(ScheduleMode.NOT_DEFINED);
		assertThat(point.activeConnectors()).extracting(Connector::getOrdinal).containsExactly(1);
	}

	@Test
	void stationCodeIsItsSerial() {
		assertThat(station().getAssignedCode()).isEqualTo("ST-1");
	}

	@Test
	void reducingConnectorsRetiresTheExtraOnesAndKeepsIds() {
		Equipment station = station();
		UUID firstId = station.activeConnectors().getFirst().getId();

		station.replaceConnectors(List.of(chademo, type2));

		assertThat(station.activeConnectors()).extracting(Connector::getOrdinal).containsExactly(1, 2);
		assertThat(station.activeConnectors().getFirst().getId()).isEqualTo(firstId);
		assertThat(station.activeConnectors().getFirst().getConnectorType()).isEqualTo(chademo);
	}

	@Test
	void inactiveConnectorTypeCannotBeAssignedToANewPosition() {
		Equipment station = station();
		type2.changeStatus(AdministrativeStatus.INACTIVE);

		station.replaceConnectors(List.of(ccs2, type2));
		assertThatThrownBy(() -> station.replaceConnectors(List.of(type2)))
				.extracting("code").isEqualTo("CONNECTOR_TYPE_INACTIVE");
	}

	@Test
	void replacementRequiresTheOldEquipmentOutOfService() {
		Equipment old = station();

		assertThatThrownBy(() -> Equipment.register(ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
				details("st-2", null, null, null), List.of(ccs2), old))
				.extracting("code").isEqualTo("REPLACED_EQUIPMENT_ACTIVE");

		old.changeStatus(AdministrativeStatus.INACTIVE);
		Equipment replacement = Equipment.register(ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
				details("st-2", null, null, null), List.of(ccs2), old);
		assertThat(replacement.getReplacesEquipmentId()).isEqualTo(old.getId());
	}

	@Test
	void urlsMustUseExpectedSchemes() {
		EquipmentDetails badMaps = new EquipmentDetails(EquipmentBrand.BENNY, "X", "S-1", null, null, null,
				"http://maps", null, null);

		assertThatThrownBy(() -> Equipment.register(ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION, badMaps,
				List.of(ccs2), null)).extracting("code").isEqualTo("INVALID_EQUIPMENT");
	}

	@Test
	void weeklyScheduleAcceptsOvernightIntervalsWithoutOverlap() {
		Equipment station = station();

		station.defineSchedule(ScheduleMode.WEEKLY, List.of(
				new WeeklyInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)),
				new WeeklyInterval(DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(6, 0)),
				new WeeklyInterval(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))),
				List.of(new ScheduleException(LocalDate.of(2026, 12, 25), true, null, null, "Navidad")));

		assertThat(station.getWeeklyIntervals()).hasSize(3);
		assertThat(station.getScheduleExceptions()).hasSize(1);
	}

	@Test
	void overnightIntervalOverlappingTheNextMorningIsRejected() {
		Equipment station = station();

		assertThatThrownBy(() -> station.defineSchedule(ScheduleMode.WEEKLY, List.of(
				new WeeklyInterval(DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(7, 0)),
				new WeeklyInterval(DayOfWeek.TUESDAY, LocalTime.of(6, 0), LocalTime.of(12, 0))), List.of()))
				.extracting("code").isEqualTo("SCHEDULE_INTERVALS_OVERLAP");
	}

	@Test
	void sundayNightOverlapsMondayMorning() {
		WeeklyInterval sunday = new WeeklyInterval(DayOfWeek.SUNDAY, LocalTime.of(23, 0), LocalTime.of(2, 0));
		WeeklyInterval monday = new WeeklyInterval(DayOfWeek.MONDAY, LocalTime.of(1, 0), LocalTime.of(3, 0));

		assertThat(sunday.overlaps(monday)).isTrue();
	}

	@Test
	void alwaysOpenDoesNotAcceptIntervalsAndExceptionsAreUniquePerDate() {
		Equipment station = station();
		WeeklyInterval interval = new WeeklyInterval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
		ScheduleException closed = new ScheduleException(LocalDate.of(2026, 1, 1), true, null, null, null);

		assertThatThrownBy(() -> station.defineSchedule(ScheduleMode.ALWAYS_OPEN, List.of(interval), List.of()))
				.extracting("code").isEqualTo("SCHEDULE_INTERVALS_NOT_ALLOWED");
		assertThatThrownBy(() -> station.defineSchedule(ScheduleMode.ALWAYS_OPEN, List.of(), List.of(closed, closed)))
				.extracting("code").isEqualTo("SCHEDULE_EXCEPTION_DUPLICATED");
	}

	@Test
	void scheduleExceptionIsClosedOrHasHours() {
		assertThatThrownBy(() -> new ScheduleException(LocalDate.of(2026, 1, 1), false, null, null, null))
				.extracting("code").isEqualTo("INVALID_SCHEDULE_EXCEPTION");
		assertThatThrownBy(() -> new ScheduleException(LocalDate.of(2026, 1, 1), true, LocalTime.NOON, null, null))
				.extracting("code").isEqualTo("INVALID_SCHEDULE_EXCEPTION");
	}

	@Test
	void imageAttachmentReturnsThePreviousReference() {
		Equipment station = station();

		assertThat(station.attachImage("ref-1")).isEmpty();
		assertThat(station.attachImage("ref-2")).contains("ref-1");
		assertThat(station.removeImage()).contains("ref-2");
		assertThat(station.getImageRef()).isNull();
	}

	@Test
	void connectorTypeCodesAreNormalizedAndCannotBeDeleted() {
		assertThat(ConnectorType.create("ccs 2", "CCS").getCode()).isEqualTo("CCS_2");
		assertThatThrownBy(() -> ConnectorType.create("x", "X")).extracting("code")
				.isEqualTo("INVALID_CONNECTOR_TYPE_CODE");
		assertThatThrownBy(() -> ccs2.changeStatus(AdministrativeStatus.DELETED)).extracting("code")
				.isEqualTo("CONNECTOR_TYPE_NOT_DELETABLE");
	}
}
