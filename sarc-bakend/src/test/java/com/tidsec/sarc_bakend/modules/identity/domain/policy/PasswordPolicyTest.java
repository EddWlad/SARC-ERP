package com.tidsec.sarc_bakend.modules.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class PasswordPolicyTest {

	@Test
	void requiresMinimumLength() {
		assertThatThrownBy(() -> PasswordPolicy.requireValid("corta-123")).isInstanceOf(BusinessRuleException.class);
		assertThatCode(() -> PasswordPolicy.requireValid("doce-caracteres")).doesNotThrowAnyException();
	}

	@Test
	void rejectsMoreThanBcryptByteLimit() {
		assertThatThrownBy(() -> PasswordPolicy.requireValid("ñ".repeat(37))).isInstanceOf(BusinessRuleException.class);
		assertThatCode(() -> PasswordPolicy.requireValid("ñ".repeat(36))).doesNotThrowAnyException();
	}
}
