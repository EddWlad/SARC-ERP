package com.tidsec.sarc_bakend.modules.incident.domain.model.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketCodeTest {

	@Test
	void padsToAtLeastThreeDigits() {
		assertThat(new TicketCode(7).text()).isEqualTo("TICKET-007");
		assertThat(new TicketCode(86).text()).isEqualTo("TICKET-086");
		assertThat(new TicketCode(999).text()).isEqualTo("TICKET-999");
	}

	@Test
	void neverTruncatesLongerNumbers() {
		assertThat(new TicketCode(1000).text()).isEqualTo("TICKET-1000");
		assertThat(new TicketCode(9001).text()).isEqualTo("TICKET-9001");
		assertThat(new TicketCode(12345).text()).isEqualTo("TICKET-12345");
	}

	@Test
	void rejectsNumbersBelowOne() {
		assertThatThrownBy(() -> new TicketCode(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketCode(-5)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parsesWrittenCodesIgnoringCaseAndOuterSpaces() {
		assertThat(TicketCode.parse("TICKET-086")).contains(new TicketCode(86));
		assertThat(TicketCode.parse("  ticket-1000 ")).contains(new TicketCode(1000));
		assertThat(TicketCode.parse("TICKET-9001")).contains(new TicketCode(9001));
	}

	@Test
	void doesNotParseWhatIsNotACode() {
		assertThat(TicketCode.parse(null)).isEmpty();
		assertThat(TicketCode.parse("")).isEmpty();
		assertThat(TicketCode.parse("TICKET-86")).isEmpty();
		assertThat(TicketCode.parse("TICKET-000")).isEmpty();
		assertThat(TicketCode.parse("TICKET086")).isEmpty();
		assertThat(TicketCode.parse("TCK-086")).isEmpty();
		assertThat(TicketCode.parse("TICKET-08a")).isEmpty();
	}

	@Test
	void roundTripsThroughText() {
		for (int number : new int[] { 1, 86, 999, 1000, 9001, 123456 }) {
			assertThat(TicketCode.parse(new TicketCode(number).text())).contains(new TicketCode(number));
		}
	}

	@Test
	void numberingIsConfiguredOnlyWithStartNextAndEnvironment() {
		assertThat(new TicketNumbering(null, null, null).configured()).isFalse();
		assertThat(new TicketNumbering(null, null, null).nextCode()).isEmpty();
		TicketNumbering configured = new TicketNumbering(9001, 9003, TicketNumbering.TEST);
		assertThat(configured.configured()).isTrue();
		assertThat(configured.nextCode()).contains(new TicketCode(9003));
		assertThat(TicketNumbering.isValidEnvironment("PRODUCTION")).isTrue();
		assertThat(TicketNumbering.isValidEnvironment("STAGING")).isFalse();
		assertThat(TicketNumbering.isValidEnvironment(null)).isFalse();
	}
}
