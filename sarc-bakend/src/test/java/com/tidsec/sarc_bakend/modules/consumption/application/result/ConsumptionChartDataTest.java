package com.tidsec.sarc_bakend.modules.consumption.application.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

class ConsumptionChartDataTest {

	private static final ConsumptionChartData.Labels LABELS =
			new ConsumptionChartData.Labels() {

				@Override
				public String day(LocalDate date) {
					return date.toString();
				}

				@Override
				public String equipment(RankedEquipment item) {
					return item.position() + ":" + item.equipment().name();
				}

				@Override
				public String source(SessionSource source) {
					return source.name();
				}

				@Override
				public String type(InfrastructureType type) {
					return type.name();
				}
			};

	private static RankedEquipment ranked(int index, String energy, int position) {
		return new RankedEquipment(
				new EquipmentReference(
						new UUID(0, index),
						ChargingNetwork.JAC,
						InfrastructureType.CHARGING_STATION,
						"E" + index,
						"C" + index),
				1,
				new BigDecimal(energy),
				position);
	}

	private static ConsumptionSummary summary() {
		var known = new EnergyTotals(3, new BigDecimal("12.345"), 2, 1);
		var noReading = new EnergyTotals(2, null, 1, 2);
		var days =
				List.of(
						new DailyEnergy(LocalDate.of(2026, 10, 1), 2, null, 2),
						new DailyEnergy(LocalDate.of(2026, 10, 2), 1, BigDecimal.ZERO, 0),
						new DailyEnergy(LocalDate.of(2026, 10, 3), 0, BigDecimal.ZERO, 0),
						new DailyEnergy(LocalDate.of(2026, 10, 4), 2, new BigDecimal("12.345"), 1));
		var top =
				List.of(
						ranked(1, "9", 1),
						ranked(2, "8", 2),
						ranked(3, "7", 3),
						ranked(4, "6", 4),
						ranked(5, "6", 4),
						ranked(6, "6", 4));
		return new ConsumptionSummary(
				LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 5),
				known,
				new BigDecimal("6.1725"),
				top,
				1,
				days,
				Map.of(
						ChargingNetwork.JAC,
						known,
						ChargingNetwork.KIA,
						noReading,
						ChargingNetwork.CONECTZ,
						EnergyTotals.EMPTY),
				Map.of(SessionSource.ENERLINK, known, SessionSource.WALLBOX, EnergyTotals.EMPTY),
				Map.of(
						InfrastructureType.CHARGING_STATION,
						known,
						InfrastructureType.CHARGING_POINT,
						EnergyTotals.EMPTY),
				null,
				null,
				null,
				null,
				null,
				EnergyTotals.EMPTY,
				Instant.EPOCH);
	}

	@Test
	void seriesKeepUnknownZeroAndNoSessionsApartWithoutRounding() {
		var charts = ConsumptionChartData.from(summary(), LABELS);

		var days = charts.dailyEnergy().points();
		assertThat(days)
				.extracting(ConsumptionChartData.Point::value)
				.containsExactly(null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12.345"));
		assertThat(days.get(0).unknown()).isTrue();
		assertThat(days.get(1).unknown()).isFalse();
		assertThat(days.get(1).withoutSessions()).isFalse();
		assertThat(days.get(2).withoutSessions()).isTrue();
		assertThat(charts.dailySessions().points())
				.extracting(ConsumptionChartData.Point::value)
				.containsExactly(
						BigDecimal.valueOf(2),
						BigDecimal.ONE,
						BigDecimal.ZERO,
						BigDecimal.valueOf(2));
	}

	@Test
	void topKeepsEveryTieWithItsRepeatedPosition() {
		var charts = ConsumptionChartData.from(summary(), LABELS);

		assertThat(charts.top().points())
				.extracting(ConsumptionChartData.Point::label)
				.containsExactly("1:E1", "2:E2", "3:E3", "4:E4", "4:E5", "4:E6");
	}

	@Test
	void distributionsMirrorTheAuthorizedSummaryAndDoNotAddApprovedTwice() {
		var summary = summary();
		var charts = ConsumptionChartData.from(summary, LABELS);

		assertThat(charts.byNetwork().points())
				.hasSize(3)
				.anyMatch(
						point ->
								point.label().equals("KIA")
										&& point.unknown()
										&& !point.withoutSessions())
				.anyMatch(point -> point.label().equals("CONECTZ") && point.withoutSessions());
		assertThat(charts.bySource().points())
				.extracting(ConsumptionChartData.Point::value)
				.contains(summary.official().energyKwh());
		assertThat(charts.byType().points()).hasSize(2);
	}

	@Test
	void anAllUnknownSeriesHasNoValuesToDraw() {
		var series =
				new ConsumptionChartData.Series(
						"t", "kWh", List.of(new ConsumptionChartData.Point("a", null, 1)));

		assertThat(series.hasValues()).isFalse();
	}
}
