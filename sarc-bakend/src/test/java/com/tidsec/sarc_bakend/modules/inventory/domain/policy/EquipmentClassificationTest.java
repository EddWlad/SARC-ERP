package com.tidsec.sarc_bakend.modules.inventory.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class EquipmentClassificationTest {

	private static final EquipmentClassification KIA_POINT = new EquipmentClassification(ChargingNetwork.KIA,
			InfrastructureType.CHARGING_POINT);
	private static final EquipmentClassification JAC_POINT = new EquipmentClassification(ChargingNetwork.JAC,
			InfrastructureType.CHARGING_POINT);
	private static final EquipmentClassification KIA_STATION = new EquipmentClassification(ChargingNetwork.KIA,
			InfrastructureType.CHARGING_STATION);

	@Test
	void kiaPointKeepsItsSixDigitCode() {
		assertThat(KIA_POINT.resolveAssignedCode("SER-1", " 123456 ")).isEqualTo("123456");
	}

	@ParameterizedTest
	@ValueSource(strings = { "012345", "12345", "1234567", "12A456", "" })
	void kiaPointRejectsInvalidCodes(String code) {
		assertThatThrownBy(() -> KIA_POINT.resolveAssignedCode("SER-1", code))
				.isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("INVALID_ASSIGNED_CODE");
	}

	@Test
	void otherEquipmentUsesTheSerialAsCode() {
		assertThat(JAC_POINT.resolveAssignedCode("SER-1", null)).isEqualTo("SER-1");
		assertThat(KIA_STATION.resolveAssignedCode("SER-2", "SER-2")).isEqualTo("SER-2");
		assertThatThrownBy(() -> JAC_POINT.resolveAssignedCode("SER-1", "999999"))
				.extracting("code").isEqualTo("ASSIGNED_CODE_IS_SERIAL");
	}

	@Test
	void pointsHaveExactlyOneConnectorAndStationsUpToThree() {
		assertThatCode(() -> KIA_POINT.requireConnectorCount(1)).doesNotThrowAnyException();
		assertThatThrownBy(() -> KIA_POINT.requireConnectorCount(2)).extracting("code")
				.isEqualTo("INVALID_CONNECTOR_COUNT");
		assertThatCode(() -> KIA_STATION.requireConnectorCount(3)).doesNotThrowAnyException();
		assertThatThrownBy(() -> KIA_STATION.requireConnectorCount(4)).extracting("code")
				.isEqualTo("INVALID_CONNECTOR_COUNT");
		assertThatThrownBy(() -> KIA_STATION.requireConnectorCount(0)).extracting("code")
				.isEqualTo("INVALID_CONNECTOR_COUNT");
	}

	@Test
	void uidPukIsMandatoryOnlyForPoints() {
		assertThatThrownBy(() -> JAC_POINT.requireUidPuk(null)).extracting("code").isEqualTo("UID_PUK_REQUIRED");
		assertThatCode(() -> KIA_STATION.requireUidPuk(null)).doesNotThrowAnyException();
	}

	@Test
	void onlyPointsHaveUsageStatus() {
		assertThatThrownBy(() -> KIA_POINT.requireUsageStatus(null)).extracting("code")
				.isEqualTo("USAGE_STATUS_REQUIRED");
		assertThatThrownBy(() -> KIA_STATION.requireUsageStatus(UsageStatus.FREE)).extracting("code")
				.isEqualTo("USAGE_STATUS_NOT_APPLICABLE");
	}
}
