package com.tidsec.sarc_bakend.shared.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReportPublicationTest {

	private static final Instant CUT_OFF = Instant.parse("2026-11-01T17:00:00Z");

	private static ReportPublication publication(Map<String, String> extra) {
		Map<String, String> criteria = new HashMap<>(
				Map.of("from", "2026-10-12", "to", "2026-10-19", "timeZone", ReportPublication.TIME_ZONE));
		criteria.putAll(extra);
		return new ReportPublication(ReportContentProfile.OFFICIAL_ONLY, CUT_OFF, criteria);
	}

	@Test
	void acceptsCurrentKeys() {
		String equipment = UUID.randomUUID().toString();

		assertThat(publication(Map.of("network", "JAC", "type", "CHARGING_STATION", "source", "ENERLINK", "equipmentId",
				equipment)).criteria()).containsEntry("equipmentId", equipment);
	}

	@Test
	void acceptsUptimeKeys() {
		String connector = UUID.randomUUID().toString();

		assertThat(publication(Map.of("periodKind", "PROVISIONAL", "periodType", "ROLLING_7D", "brand", "Kia",
				"connectorId", connector)).criteria()).containsEntry("connectorId", connector)
						.containsEntry("periodKind", "PROVISIONAL");
	}

	@Test
	void rejectsUnknownOrInvalidValues() {
		assertThatThrownBy(() -> publication(Map.of("periodo", "x"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("periodKind", "SEMANAL")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("periodType", "ROLLING_1H")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("brand", " "))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("connectorId", "no-uuid")))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
