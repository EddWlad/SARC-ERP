package com.tidsec.sarc_bakend.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class EmailTest {

	@Test
	void isTrimmedAndLowercased() {
		assertThat(new Email("  Ana.Perez@EOS.com ").value()).isEqualTo("ana.perez@eos.com");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "sin-arroba", "a@b", "a b@c.co", "@c.co" })
	void malformedEmailIsRejected(String raw) {
		assertThatThrownBy(() -> new Email(raw)).isInstanceOf(BusinessRuleException.class);
	}
}
