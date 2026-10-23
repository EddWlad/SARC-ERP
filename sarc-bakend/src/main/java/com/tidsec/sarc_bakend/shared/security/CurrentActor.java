package com.tidsec.sarc_bakend.shared.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resuelve el usuario que ejecuta la operacion actual, si existe. */
@Component
public class CurrentActor {

	public Optional<UUID> id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
			return Optional.of(principal.id());
		}
		return Optional.empty();
	}

	/** True si el usuario actual tiene alguno de los roles indicados (sin el prefijo ROLE_). */
	public boolean hasAnyRole(String... roles) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> Arrays.stream(roles).anyMatch(role -> authority.getAuthority().equals("ROLE_" + role)));
	}
}
