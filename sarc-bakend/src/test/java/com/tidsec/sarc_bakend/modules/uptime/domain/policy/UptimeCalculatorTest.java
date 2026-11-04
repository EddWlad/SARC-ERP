package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.EQUIPMENT_A;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.EQUIPMENT_B;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.WEEK_1;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.WEEK_2;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.connector;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.pending;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.week;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.window;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

class UptimeCalculatorTest {

	private final MeasurableConnector a1 = connector(EQUIPMENT_A, "Alfa", 1);
	private final MeasurableConnector a2 = connector(EQUIPMENT_A, "Alfa", 2);
	private final MeasurableConnector b1 = connector(EQUIPMENT_B, "Beta", 1);

	@Test
	void aggregatesByMinutesNotByPercentages() {
		var units = UptimeCalculator.officialUnits(List.of(a1, a2),
				List.of(week(a1, WEEK_1, 1000, 0), week(a2, WEEK_1, 9000, 900)), List.of(WEEK_1));

		assertThat(UptimeCalculator.kpis(units).uptimePercent()).isEqualByComparingTo("91.00");
	}

	@Test
	void reportsHoursAndZeroPercentIsIncluded() {
		var units = UptimeCalculator.officialUnits(List.of(a1), List.of(week(a1, WEEK_1, 1440, 1440)), List.of(WEEK_1));

		var kpis = UptimeCalculator.kpis(units);
		assertThat(kpis.uptimePercent()).isEqualByComparingTo("0.00");
		assertThat(kpis.offlineHours()).isEqualByComparingTo("24.00");
		assertThat(kpis.connectorsIncluded()).isEqualTo(1);
	}

	@Test
	void noDataGivesNullNeverZero() {
		var units = UptimeCalculator.officialUnits(List.of(a1), List.of(), List.of(WEEK_1));

		assertThat(UptimeCalculator.kpis(units).uptimePercent()).isNull();
	}

	@Test
	void provisionalUsesMostRecentWindowOfEachConnector() {
		var older = window(a1, PeriodType.ROLLING_24H, WEEK_1, 1440, 1440);
		var newer = window(a1, PeriodType.ROLLING_24H, WEEK_2, 1440, 0);

		var units = UptimeCalculator.provisionalUnits(List.of(a1), List.of(older, newer));

		assertThat(UptimeCalculator.kpis(units).uptimePercent()).isEqualByComparingTo("100.00");
		assertThat(UptimeCalculator.kpis(units).evaluatedMinutes()).isEqualTo(1440);
	}

	@Test
	void overlappingWindowsAreNotDoubleCounted() {
		var first = window(a1, PeriodType.ROLLING_7D, WEEK_1, 10080, 0);
		var overlap = window(a1, PeriodType.ROLLING_7D, WEEK_1.plusSeconds(86400), 10080, 0);

		var units = UptimeCalculator.provisionalUnits(List.of(a1), List.of(first, overlap));

		assertThat(UptimeCalculator.kpis(units).evaluatedMinutes()).isEqualTo(10080);
	}

	@Test
	void equipmentEvaluatedAndWithoutData() {
		var units = UptimeCalculator.officialUnits(List.of(a1, a2, b1), List.of(week(a1, WEEK_1, 1440, 0)),
				List.of(WEEK_1));

		var kpis = UptimeCalculator.kpis(units);
		assertThat(kpis.equipmentEvaluated()).isEqualTo(1);
		assertThat(kpis.equipmentWithoutData()).isEqualTo(1);
	}

	@Test
	void equipmentSummaryCountsConnectorWeekPairs() {
		var units = UptimeCalculator.officialUnits(List.of(a1, a2),
				List.of(week(a1, WEEK_1, 1000, 100), week(a1, WEEK_2, 1000, 100), week(a2, WEEK_1, 1000, 100)),
				List.of(WEEK_1, WEEK_2));

		var result = UptimeCalculator.equipment(units).getFirst();
		assertThat(result.connectorsIncluded()).isEqualTo(3);
		assertThat(result.connectorsInScope()).isEqualTo(4);
		assertThat(result.uptimePercent()).isEqualByComparingTo(new BigDecimal("90.00"));
	}

	@Test
	void pendingNeverReachesTheFigure() {
		var units = UptimeCalculator.provisionalUnits(List.of(a1),
				List.of(pending(a1, PeriodType.ROLLING_24H, WEEK_1)));

		assertThat(UptimeCalculator.kpis(units).uptimePercent()).isNull();
	}

	@Test
	void reportedUptimeWeighsTheWholeWeekAndDiscountsWhatItLeavesOut() {
		var units = UptimeCalculator.officialUnits(List.of(a1, b1),
				List.of(UptimeFacts.reported(a1, WEEK_1, "99.40"), UptimeFacts.reported(b1, WEEK_1, "100")),
				List.of(WEEK_1));

		assertThat(UptimeCalculator.kpis(units).uptimePercent()).isEqualByComparingTo("99.70");
	}
}
