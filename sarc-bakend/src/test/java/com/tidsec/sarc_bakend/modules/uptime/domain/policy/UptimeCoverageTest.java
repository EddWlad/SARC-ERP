package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.EQUIPMENT_A;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.WEEK_1;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.WEEK_2;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.connector;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.excluded;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.pending;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.special;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.week;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.window;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.UnitState;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

class UptimeCoverageTest {

	private static final PeriodType TYPE = PeriodType.ROLLING_24H;

	private final MeasurableConnector c1 = connector(EQUIPMENT_A, "Alfa", 1);
	private final MeasurableConnector c2 = connector(EQUIPMENT_A, "Alfa", 2);
	private final MeasurableConnector c3 = connector(EQUIPMENT_A, "Alfa", 3);

	private UptimeCalculator.Coverage provisional(List<MeasurableConnector> connectors,
			com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact... facts) {
		return UptimeCalculator.coverage(UptimeCalculator.provisionalUnits(connectors, List.of(facts)));
	}

	@Test
	void noDataWithoutMeasurementAndPendingStayInDenominator() {
		var coverage = provisional(List.of(c1, c2, c3), special(c1, TYPE, WEEK_1, ReadingClass.NO_DATA),
				pending(c2, TYPE, WEEK_1));

		assertThat(coverage.denominator()).isEqualTo(3);
		assertThat(coverage.included()).isZero();
		assertThat(coverage.percent()).isEqualByComparingTo("0.00");
		assertThat(coverage.byState()).containsAllEntriesOf(Map.of(UnitState.NO_DATA, 1, UnitState.PENDING_REVIEW, 1,
				UnitState.WITHOUT_MEASUREMENT, 1));
	}

	@Test
	void outOfScopeAndRetiredLeaveDenominator() {
		var coverage = provisional(List.of(c1, c2, c3), window(c1, TYPE, WEEK_1, 1440, 0),
				special(c2, TYPE, WEEK_1, ReadingClass.OUT_OF_SCOPE), special(c3, TYPE, WEEK_1, ReadingClass.RETIRED));

		assertThat(coverage.denominator()).isEqualTo(1);
		assertThat(coverage.percent()).isEqualByComparingTo("100.00");
	}

	@Test
	void excludedStaysInDenominatorWithoutCovering() {
		var coverage = provisional(List.of(c1), excluded(c1, TYPE, WEEK_1));

		assertThat(coverage.denominator()).isEqualTo(1);
		assertThat(coverage.included()).isZero();
		assertThat(coverage.byState()).containsEntry(UnitState.EXCLUDED, 1);
	}

	@Test
	void inactiveEntersOnlyWithMeasurement() {
		var inactive = connector(EQUIPMENT_A, "Alfa", 4, false, AdministrativeStatus.ACTIVE);

		assertThat(provisional(List.of(c1, inactive), window(inactive, TYPE, WEEK_1, 1440, 0)).denominator())
				.isEqualTo(2);
		assertThat(provisional(List.of(c1, inactive)).denominator()).isEqualTo(1);
	}

	@Test
	void inactiveEquipmentWithoutMeasurementDoesNotAppear() {
		var ofInactive = connector(EQUIPMENT_A, "Alfa", 5, true, AdministrativeStatus.INACTIVE);

		assertThat(UptimeCalculator.provisionalUnits(List.of(ofInactive), List.of())).isEmpty();
	}

	@Test
	void retiredConnectorWithoutMeasurementIsListedAsRetired() {
		var retired = connector(EQUIPMENT_A, "Alfa", 4, false, AdministrativeStatus.ACTIVE);
		var units = UptimeCalculator.provisionalUnits(List.of(retired), List.of());

		assertThat(UptimeCalculator.exclusions(units, false)).singleElement()
				.satisfies(item -> assertThat(item.state()).isEqualTo(UnitState.RETIRED));
	}

	@Test
	void excludedOnlyListedForOperationalRoles() {
		var units = UptimeCalculator.provisionalUnits(List.of(c1), List.of(excluded(c1, TYPE, WEEK_1)));

		assertThat(UptimeCalculator.exclusions(units, false)).isEmpty();
		assertThat(UptimeCalculator.exclusions(units, true)).hasSize(1);
	}

	@Test
	void officialCountsConnectorWeekPairs() {
		var units = UptimeCalculator.officialUnits(List.of(c1, c2), List.of(week(c1, WEEK_1, 1440, 0),
				week(c1, WEEK_2, 1440, 0), week(c2, WEEK_1, 1440, 0)), List.of(WEEK_1, WEEK_2));

		var coverage = UptimeCalculator.coverage(units);
		assertThat(coverage.included()).isEqualTo(3);
		assertThat(coverage.denominator()).isEqualTo(4);
		assertThat(coverage.percent()).isEqualByComparingTo("75.00");
	}

	@Test
	void oneStatePerUnitWithPrecedence() {
		var coverage = provisional(List.of(c1), pending(c1, TYPE, WEEK_2), special(c1, TYPE, WEEK_1, ReadingClass.NO_DATA),
				excluded(c1, TYPE, WEEK_1.plusSeconds(60)));

		assertThat(coverage.byState()).containsEntry(UnitState.EXCLUDED, 1);
		assertThat(coverage.byState().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
	}

	@Test
	void zeroDenominatorGivesNull() {
		var coverage = provisional(List.of(c1), special(c1, TYPE, WEEK_1, ReadingClass.RETIRED));

		assertThat(coverage.denominator()).isZero();
		assertThat(coverage.percent()).isNull();
	}
}
