package com.tidsec.sarc_bakend.modules.identity.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.security.AuthenticatedPrincipal;

/**
 * Autentica credenciales y lleva el conteo de intentos fallidos. El fallo no revierte la transaccion
 * para que el contador y el bloqueo temporal si queden persistidos.
 */
@Service
@Transactional(noRollbackFor = AuthenticationException.class)
public class LoginService {

	private final AuthenticationManager authenticationManager;
	private final UserRepository users;
	private final LoginPolicy loginPolicy;
	private final Clock clock;

	LoginService(AuthenticationManager authenticationManager, UserRepository users, LoginPolicy loginPolicy,
			Clock clock) {
		this.authenticationManager = authenticationManager;
		this.users = users;
		this.loginPolicy = loginPolicy;
		this.clock = clock;
	}

	public Authentication login(String email, String password) {
		String normalizedEmail = Email.normalize(email);
		try {
			Authentication authentication = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
			AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
			users.findById(principal.id()).ifPresent(user -> {
				user.registerSuccessfulLogin();
				users.save(user);
			});
			return authentication;
		}
		catch (AuthenticationException ex) {
			Instant now = Instant.now(clock);
			users.findByEmail(normalizedEmail).ifPresent(user -> {
				user.registerFailedLogin(now, loginPolicy);
				users.save(user);
			});
			throw ex;
		}
	}
}
