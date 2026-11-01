package com.tidsec.sarc_bakend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class ConsumptionReadAccessTest {
	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void onlyAuthorizedRoleUnionGrantsOperationalRead() {
		var access = new ConsumptionReadAccess(new CurrentActor());
		assertThat(access.canReadOperational()).isFalse();
		for (String authorities :
				new String[] {
					"ROLE_GERENTE", "ROLE_SUPERVISOR,ROLE_ASISTENTE", "PASSWORD_CHANGE_REQUIRED"
				}) {
			SecurityContextHolder.getContext()
					.setAuthentication(
							new UsernamePasswordAuthenticationToken(
									"qa",
									"",
									AuthorityUtils.commaSeparatedStringToAuthorityList(
											authorities)));
			assertThat(access.canReadOperational()).isFalse();
		}
		for (String authorities :
				new String[] {"ROLE_ADMINISTRADOR", "ROLE_ANALISTA,ROLE_GERENTE"}) {
			SecurityContextHolder.getContext()
					.setAuthentication(
							new UsernamePasswordAuthenticationToken(
									"qa",
									"",
									AuthorityUtils.commaSeparatedStringToAuthorityList(
											authorities)));
			assertThat(access.canReadOperational()).isTrue();
		}
	}
}
