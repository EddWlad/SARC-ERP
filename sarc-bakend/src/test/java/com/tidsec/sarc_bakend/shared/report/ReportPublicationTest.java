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
	@Test
	void acceptsTheIntermittencyPeriodPresetsAndPointFilter() {
		String first = UUID.randomUUID().toString();
		String second = UUID.randomUUID().toString();

		for (String preset : java.util.List.of("FIRST_HALF", "SECOND_HALF", "LAST_15_DAYS", "CUSTOM")) {
			assertThat(publication(Map.of("periodPreset", preset, "equipmentIds", first + "," + second)).criteria())
					.containsEntry("periodPreset", preset).containsEntry("equipmentIds", first + "," + second);
		}
	}

	@Test
	void rejectsAnInvalidPresetOrPointFilter() {
		assertThatThrownBy(() -> publication(Map.of("periodPreset", "MENSUAL")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("equipmentIds", "no-uuid")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("equipmentIds", UUID.randomUUID() + ",")))
				.isInstanceOf(IllegalArgumentException.class);
		String tooMany = java.util.stream.IntStream.range(0, 201).mapToObj(index -> UUID.randomUUID().toString())
				.collect(java.util.stream.Collectors.joining(","));
		assertThatThrownBy(() -> publication(Map.of("equipmentIds", tooMany)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void uptimePeriodTypesStayExactlyAsTheyWere() {
		// Los periodos de intermitencias son una lista aparte: no se cuelan como tipo de periodo de Uptime.
		assertThatThrownBy(() -> publication(Map.of("periodType", "FIRST_HALF")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publication(Map.of("periodType", "CUSTOM")))
				.isInstanceOf(IllegalArgumentException.class);
		for (String type : java.util.List.of("ROLLING_24H", "ROLLING_7D", "ROLLING_30D", "EXACT_WEEK")) {
			assertThat(publication(Map.of("periodType", type)).criteria()).containsEntry("periodType", type);
		}
	}

    private Map<String,String> reference() {return new HashMap<>(Map.of("selectionBasis","REFERENCE_WEEK","referenceWeek","2026-10-26",
        "periodType","ROLLING_7D","periodKind","PROVISIONAL","timeZone",ReportPublication.TIME_ZONE));}
    @Test void referenceNeedsNoFabricatedBounds() {
        assertThat(new ReportPublication(ReportContentProfile.OFFICIAL_ONLY,CUT_OFF,reference()).criteria()).doesNotContainKeys("from","to");
    }
    @Test void rejectsMixedOrInvalidReferenceCriteria() {
        for(var change:java.util.List.of(Map.of("from","2026-10-26"),Map.of("referenceWeek","2026-10-27"),
            Map.of("periodType","EXACT_WEEK"),Map.of("temporalSection","REQUIRES_RECONCILIATION"))) {
            var values=reference();values.putAll(change);
            assertThatThrownBy(()->new ReportPublication(ReportContentProfile.OFFICIAL_ONLY,CUT_OFF,values)).isInstanceOf(IllegalArgumentException.class);
        }
    }

}
