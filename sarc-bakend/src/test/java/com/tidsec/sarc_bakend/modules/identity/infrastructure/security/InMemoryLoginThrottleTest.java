package com.tidsec.sarc_bakend.modules.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginThrottlePolicy;
import com.tidsec.sarc_bakend.shared.error.TooManyRequestsException;

class InMemoryLoginThrottleTest {

	private static final Instant START = Instant.parse("2026-10-23T12:00:00Z");

	private final MutableClock clock = new MutableClock(START);
	private final InMemoryLoginThrottle throttle = new InMemoryLoginThrottle(
			new LoginThrottlePolicy(3, Duration.ofMinutes(15)), clock);

	@Test
	void blocksAClientOnlyAfterItsAllowedFailures() {
		throttle.registerFailure("10.0.0.1");
		throttle.registerFailure("10.0.0.1");
		assertThatCode(() -> throttle.requireAllowed("10.0.0.1")).doesNotThrowAnyException();

		throttle.registerFailure("10.0.0.1");

		assertThatThrownBy(() -> throttle.requireAllowed("10.0.0.1"))
				.isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
					assertThat(ex.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
					assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(15));
				});
		assertThatCode(() -> throttle.requireAllowed("10.0.0.2")).doesNotThrowAnyException();
	}

	@Test
	void releasesTheClientWhenItsWindowEnds() {
		for (int i = 0; i < 3; i++) {
			throttle.registerFailure("10.0.0.1");
		}
		clock.advance(Duration.ofMinutes(10));
		assertThatThrownBy(() -> throttle.requireAllowed("10.0.0.1"))
				.isInstanceOfSatisfying(TooManyRequestsException.class,
						ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(5)));

		clock.advance(Duration.ofMinutes(5));

		assertThatCode(() -> throttle.requireAllowed("10.0.0.1")).doesNotThrowAnyException();
		throttle.registerFailure("10.0.0.1");
		assertThatCode(() -> throttle.requireAllowed("10.0.0.1")).doesNotThrowAnyException();
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
