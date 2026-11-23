package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.policy.RetroactivePolicy.Kind;

/** FR-018 y FR-041: mas de un minuto antes del registro es retroactivo; una hora futura se rechaza. */
class RetroactivePolicyTest {

	private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

	@Test
	void theSameInstantIsNotRetroactive() {
		assertThat(RetroactivePolicy.evaluate(NOW, NOW)).isEqualTo(Kind.NOW);
	}

	@Test
	void aDifferenceOfOneMinuteOrLessIsNotRetroactive() {
		assertThat(RetroactivePolicy.evaluate(NOW.minus(Duration.ofSeconds(30)), NOW)).isEqualTo(Kind.NOW);
		assertThat(RetroactivePolicy.evaluate(NOW.minus(Duration.ofMinutes(1)), NOW)).isEqualTo(Kind.NOW);
	}

	@Test
	void moreThanOneMinuteBeforeIsRetroactive() {
		assertThat(RetroactivePolicy.evaluate(NOW.minus(Duration.ofMinutes(1)).minusMillis(1), NOW))
				.isEqualTo(Kind.RETROACTIVE);
		assertThat(RetroactivePolicy.evaluate(NOW.minus(Duration.ofMinutes(2)), NOW)).isEqualTo(Kind.RETROACTIVE);
		assertThat(RetroactivePolicy.evaluate(NOW.minus(Duration.ofDays(3)), NOW)).isEqualTo(Kind.RETROACTIVE);
	}

	@Test
	void aFutureInstantIsRejected() {
		assertThat(RetroactivePolicy.evaluate(NOW.plusMillis(1), NOW)).isEqualTo(Kind.FUTURE);
		assertThat(RetroactivePolicy.evaluate(NOW.plus(Duration.ofHours(1)), NOW)).isEqualTo(Kind.FUTURE);
	}
}
