package com.tidsec.sarc_bakend.shared.security;

public final class SecurityAuthorities {

	/** Autoridad unica de quien debe cambiar su contrasena temporal; no concede acceso a ningun modulo. */
	public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

	private SecurityAuthorities() {
	}
}
