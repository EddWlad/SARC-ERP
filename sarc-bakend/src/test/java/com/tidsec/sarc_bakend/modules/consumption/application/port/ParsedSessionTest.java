package com.tidsec.sarc_bakend.modules.consumption.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.policy.SessionRules;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

class ParsedSessionTest {

	@Test
	void onlyTheEnergyObservationMayAccompanyAValidReading() {
		SessionReading reading = new SessionReading(Instant.parse("2026-10-21T15:00:00Z"), null,
				new BigDecimal("1500"), "A", "C", null, null);
		List<RowIssue> observation = List.of(new RowIssue("ENERGY_ABOVE_THRESHOLD", "Por revisar"));
		assertThat(new ParsedSession(1, "1", reading, null, observation).isValid()).isTrue();
		assertThat(new ParsedSession(1, "1", null, null, observation).isValid()).isFalse();
		assertThat(new ParsedSession(1, "1", reading, null,
				List.of(observation.getFirst(), new RowIssue("UNKNOWN_ERROR", "Error"))).isValid()).isFalse();
		assertThat(SessionRules.check(reading)).extracting(RowIssue::code).containsExactly("ENERGY_ABOVE_THRESHOLD");
	}
}
