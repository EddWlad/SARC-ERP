package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.EQUIPMENT_A;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.EQUIPMENT_B;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.WEEK_1;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.WEEK_2;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.connector;
import static com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeFacts.week;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

class UptimeRankingTest {

	private final MeasurableConnector beta = connector(EQUIPMENT_B, "Beta", 1);
	private final MeasurableConnector alfa = connector(EQUIPMENT_A, "Alfa", 1);
	private final MeasurableConnector gamma = connector(new UUID(1, 3), "Gamma", 1);

	private List<UptimeCalculator.ConnectorResult> rank(List<MeasurableConnector> connectors, List<MeasurementFact> facts) {
		return UptimeCalculator.ranking(UptimeCalculator.officialUnits(connectors, facts, List.of(WEEK_1, WEEK_2)));
	}

	@Test
	void ordersLowestFirst() {
		var ranking = rank(List.of(alfa, beta), List.of(week(alfa, WEEK_1, 1000, 0), week(beta, WEEK_1, 1000, 500)));

		assertThat(ranking).extracting(item -> item.connector().equipmentName()).containsExactly("Beta", "Alfa");
	}

	@Test
	void tieOnPresentedValueSharesPositionAndOrdersByName() {
		var ranking = rank(List.of(beta, alfa, gamma), List.of(week(beta, WEEK_1, 100000, 1), week(alfa, WEEK_1, 100000, 2),
				week(gamma, WEEK_1, 100000, 90000)));

		assertThat(ranking).extracting(UptimeCalculator.ConnectorResult::position).containsExactly(1, 2, 2);
		assertThat(ranking).extracting(item -> item.connector().equipmentName()).containsExactly("Gamma", "Alfa", "Beta");
	}

	@Test
	void sumsMinutesAcrossWeeks() {
		var ranking = rank(List.of(alfa), List.of(week(alfa, WEEK_1, 1000, 1000), week(alfa, WEEK_2, 3000, 0)));

		assertThat(ranking.getFirst().uptimePercent()).isEqualByComparingTo("75.00");
		assertThat(ranking).hasSize(1);
	}

	@Test
	void includesEveryEvaluatedConnectorWithoutTopCut() {
		List<MeasurableConnector> many = new ArrayList<>();
		List<MeasurementFact> facts = new ArrayList<>();
		for (int ordinal = 1; ordinal <= 25; ordinal++) {
			MeasurableConnector item = connector(EQUIPMENT_A, "Alfa", ordinal);
			many.add(item);
			facts.add(week(item, WEEK_1, 1000, ordinal));
		}

		assertThat(rank(many, facts)).hasSize(25);
	}

	@Test
	void connectorWithoutMeasurementIsNotRanked() {
		assertThat(rank(List.of(alfa, beta), List.of(week(alfa, WEEK_1, 1000, 0)))).hasSize(1);
	}
}
