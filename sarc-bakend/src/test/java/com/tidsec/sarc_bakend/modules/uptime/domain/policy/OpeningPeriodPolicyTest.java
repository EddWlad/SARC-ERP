package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType.EXACT_WEEK;
import static com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType.ROLLING_24H;
import static com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType.ROLLING_30D;
import static com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType.ROLLING_7D;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy.Window;

class OpeningPeriodPolicyTest {

	private static final Instant END = Instant.parse("2026-10-31T19:00:00Z");

	private static Window window(PeriodType type, Instant end) {
		return new Window(type, end.minus(type.nominal()), end);
	}

	@Test
	void opensOnMostRecentOfficialWeek() {
		Instant older = Instant.parse("2026-10-19T05:00:00Z");
		Instant newer = Instant.parse("2026-10-26T05:00:00Z");

		var opening = OpeningPeriodPolicy.resolve(
				List.of(window(EXACT_WEEK, older), window(EXACT_WEEK, newer), window(ROLLING_7D, END)));

		assertThat(opening.kind()).isEqualTo(OpeningPeriodPolicy.Kind.OFFICIAL);
		assertThat(opening.to()).isEqualTo(newer);
	}

	@Test
	void prefersSevenDaysWithoutOfficial() {
		var opening = OpeningPeriodPolicy.resolve(List.of(window(ROLLING_24H, END.plusSeconds(3600)),
				window(ROLLING_7D, END.minusSeconds(86400)), window(ROLLING_30D, END)));

		assertThat(opening.kind()).isEqualTo(OpeningPeriodPolicy.Kind.PROVISIONAL);
		assertThat(opening.type()).isEqualTo(ROLLING_7D);
	}

	@Test
	void withoutSevenDaysTakesMostRecent() {
		var opening = OpeningPeriodPolicy
				.resolve(List.of(window(ROLLING_24H, END), window(ROLLING_30D, END.minusSeconds(60))));

		assertThat(opening.type()).isEqualTo(ROLLING_24H);
	}

	@Test
	void tieOnEndTakesShortestDuration() {
		var opening = OpeningPeriodPolicy.resolve(List.of(window(ROLLING_30D, END), window(ROLLING_24H, END)));

		assertThat(opening.type()).isEqualTo(ROLLING_24H);
	}

	@Test
	void rangeCoversOneNominalDurationBeforeLatest() {
		var recent = window(ROLLING_24H, END);
		var insideRange = window(ROLLING_24H, END.minusSeconds(3600));
		var tooOld = window(ROLLING_24H, END.minus(ROLLING_24H.nominal()));

		var range = OpeningPeriodPolicy.range(ROLLING_24H, List.of(recent, insideRange, tooOld));

		assertThat(range.includes(recent.end())).isTrue();
		assertThat(range.includes(insideRange.end())).isTrue();
		assertThat(range.includes(tooOld.end())).isFalse();
	}

	@Test
	void provisionalBlockKeepsPreferenceBesideOfficial() {
		var windows = List.of(window(EXACT_WEEK, END), window(ROLLING_24H, END), window(ROLLING_7D, END.minusSeconds(60)));

		assertThat(OpeningPeriodPolicy.preferredProvisional(windows)).contains(ROLLING_7D);
	}

	@Test
	void noWindowsMeansNothingToOpen() {
		assertThat(OpeningPeriodPolicy.resolve(List.of()).kind()).isEqualTo(OpeningPeriodPolicy.Kind.NONE);
		assertThat(OpeningPeriodPolicy.preferredProvisional(List.of())).isEmpty();
	}
}
