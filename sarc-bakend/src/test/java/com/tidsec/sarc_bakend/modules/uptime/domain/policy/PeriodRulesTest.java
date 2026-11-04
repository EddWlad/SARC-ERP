package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class PeriodRulesTest {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final Instant MONDAY = OffsetDateTime.parse("2026-10-12T00:00:00-05:00").toInstant();
	private static final Instant NEXT_MONDAY = OffsetDateTime.parse("2026-10-19T00:00:00-05:00").toInstant();
	private static final Instant NOW = OffsetDateTime.parse("2026-11-01T12:00:00-05:00").toInstant();

	private static void assertCode(Instant start, Instant end, PeriodType type, boolean confirmed, String code) {
		assertThatThrownBy(() -> PeriodRules.check(start, end, type, confirmed, NOW, ZONE))
				.isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code", code);
	}

	@Test
	void mondayToMondayInOperatingZoneIsExactWeek() {
		assertThat(PeriodRules.isAlignedWeek(MONDAY, NEXT_MONDAY, ZONE)).isTrue();
		assertThatCode(() -> PeriodRules.check(MONDAY, NEXT_MONDAY, PeriodType.EXACT_WEEK, true, NOW, ZONE))
				.doesNotThrowAnyException();
	}

	@Test
	void mondayInUtcIsNotMondayInGuayaquil() {
		Instant utcMonday = Instant.parse("2026-10-12T00:00:00Z");
		assertThat(PeriodRules.isAlignedWeek(utcMonday, utcMonday.plusSeconds(7 * 86400), ZONE)).isFalse();
	}

	@Test
	void exactWeekMustBeAligned() {
		assertCode(MONDAY.plusSeconds(3600), NEXT_MONDAY, PeriodType.EXACT_WEEK, true,
				"UPTIME_EXACT_WEEK_NOT_ALIGNED");
	}

	@Test
	void exactWeekNeedsConfirmation() {
		assertCode(MONDAY, NEXT_MONDAY, PeriodType.EXACT_WEEK, false, "UPTIME_EXACT_WEEK_NOT_CONFIRMED");
	}

	@Test
	void alignedWindowWithoutConfirmationStaysProvisional() {
		assertThatCode(() -> PeriodRules.check(MONDAY, NEXT_MONDAY, PeriodType.ROLLING_7D, false, NOW, ZONE))
				.doesNotThrowAnyException();
	}

	@Test
	void endIsExclusiveAndCanEqualNow() {
		assertThatCode(() -> PeriodRules.check(NOW.minusSeconds(86400), NOW, PeriodType.ROLLING_24H, false, NOW, ZONE))
				.doesNotThrowAnyException();
	}

	@Test
	void endInTheFutureIsRejected() {
		assertCode(NOW, NOW.plusSeconds(86400), PeriodType.ROLLING_24H, false, "UPTIME_PERIOD_FUTURE");
	}

	@Test
	void endMustFollowStart() {
		assertCode(MONDAY, MONDAY, PeriodType.ROLLING_24H, false, "UPTIME_PERIOD_INVALID");
		assertCode(NEXT_MONDAY, MONDAY, PeriodType.ROLLING_24H, false, "UPTIME_PERIOD_INVALID");
	}

	@Test
	void durationIsInMinutes() {
		assertThat(PeriodRules.durationMinutes(MONDAY, NEXT_MONDAY)).isEqualTo(10080);
	}
}
