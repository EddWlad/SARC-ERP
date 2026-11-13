package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ReviewReason;

class WindowPoliciesTest {

	private static final Instant NOW = Instant.parse("2026-10-15T19:05:20Z");

	@Test
	void theCurrentMinuteIsNotFutureButTheNextOneIs() {
		assertThat(FutureInstantPolicy.isFuture(Instant.parse("2026-10-15T19:05:00Z"), NOW)).isFalse();
		assertThat(FutureInstantPolicy.isFuture(Instant.parse("2026-10-15T19:04:00Z"), NOW)).isFalse();
		assertThat(FutureInstantPolicy.isFuture(Instant.parse("2026-10-15T19:06:00Z"), NOW)).isTrue();
		assertThat(FutureInstantPolicy.isFuture(Instant.parse("2026-10-16T19:05:00Z"), NOW)).isTrue();
	}

	@Test
	void theLastSecondOfTheCurrentMinuteStillCountsAsCurrent() {
		Instant lastSecond = Instant.parse("2026-10-15T19:05:59Z");

		assertThat(FutureInstantPolicy.isFuture(Instant.parse("2026-10-15T19:05:00Z"), lastSecond)).isFalse();
		assertThat(FutureInstantPolicy.isFuture(Instant.parse("2026-10-15T19:06:00Z"), lastSecond)).isTrue();
	}

	@Test
	void aWindowOf240MinutesOrMoreNeedsReview() {
		assertThat(LongWindowPolicy.isLong(239)).isFalse();
		assertThat(LongWindowPolicy.isLong(240)).isTrue();
		assertThat(LongWindowPolicy.reasonsFor(239)).isEmpty();
		assertThat(LongWindowPolicy.reasonsFor(240)).containsExactly(ReviewReason.LONG_WINDOW);
		assertThat(LongWindowPolicy.reasonsFor(979)).containsExactly(ReviewReason.LONG_WINDOW);
	}

	@Test
	void formatsAccumulatedHoursAndMinutes() {
		assertThat(DurationText.hoursAndMinutes(2202)).isEqualTo("36:42");
		assertThat(DurationText.hoursAndMinutes(109)).isEqualTo("1:49");
		assertThat(DurationText.hoursAndMinutes(5)).isEqualTo("0:05");
		assertThat(DurationText.hoursAndMinutes(0)).isEqualTo("0:00");
		assertThat(DurationText.hoursAndMinutes(1440)).isEqualTo("24:00");
		assertThatThrownBy(() -> DurationText.hoursAndMinutes(-1)).isInstanceOf(IllegalArgumentException.class);
	}
}
