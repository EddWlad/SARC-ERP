package com.tidsec.sarc_bakend.modules.identity.domain.policy;

import java.nio.charset.StandardCharsets;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** Requisitos de una contrasena definida por el usuario. BCrypt solo considera los primeros 72 bytes. */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 12;
	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	public static void requireValid(String password) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw new BusinessRuleException("WEAK_PASSWORD",
					"La contrasena debe tener al menos " + MIN_LENGTH + " caracteres");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw new BusinessRuleException("WEAK_PASSWORD",
					"La contrasena no puede superar " + MAX_BYTES + " bytes");
		}
	}
}
