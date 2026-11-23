package com.tidsec.sarc_bakend.modules.reporting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T091: los informes de incidencias no se restringen y los tipos existentes no cambian. */
class IssuedReportKindsTest {

	@Test
	void theIncidentReportsAreNotRestrictable() {
		assertThat(IssuedReportKinds.restrictable(IssuedReportKinds.INCIDENT_CONTROL)).isFalse();
		assertThat(IssuedReportKinds.restrictable(IssuedReportKinds.INCIDENT_MANAGEMENT)).isFalse();
		assertThat(IssuedReportKinds.INCIDENT_CONTROL).isEqualTo("INCIDENT_CONTROL");
		assertThat(IssuedReportKinds.INCIDENT_MANAGEMENT).isEqualTo("INCIDENT_MANAGEMENT");
	}

	@Test
	void theExistingRestrictableKindsStayTheSame() {
		assertThat(IssuedReportKinds.restrictable("CONSUMPTION")).isTrue();
		assertThat(IssuedReportKinds.restrictable("INTERMITTENCY")).isTrue();
		assertThat(IssuedReportKinds.restrictable("UPTIME")).isFalse();
		assertThat(IssuedReportKinds.restrictable("DAILY")).isFalse();
		assertThat(IssuedReportKinds.restrictable(null)).isFalse();
	}
}
