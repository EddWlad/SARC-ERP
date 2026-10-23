package com.tidsec.sarc_bakend.shared.security;

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
}
