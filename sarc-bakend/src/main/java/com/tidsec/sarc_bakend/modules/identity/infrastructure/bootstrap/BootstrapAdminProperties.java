package com.tidsec.sarc_bakend.modules.identity.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("sarc.bootstrap-admin")
record BootstrapAdminProperties(String email, String password, @DefaultValue("Administrador") String fullName) {

	boolean isConfigured() {
		return hasText(email) && hasText(password);
	}

	boolean isPartiallyConfigured() {
		return hasText(email) != hasText(password);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
