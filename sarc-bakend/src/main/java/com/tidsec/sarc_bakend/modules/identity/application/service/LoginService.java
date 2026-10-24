package com.tidsec.sarc_bakend.modules.identity.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.identity.application.port.LoginThrottle;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.security.AuthenticatedPrincipal;

/**
 * Autentica credenciales y lleva el conteo de intentos fallidos. El fallo no revierte la transaccion
 * para que el contador y el bloqueo temporal si queden persistidos; el repositorio los actualiza
 * de forma atomica, asi que intentos en paralelo cuentan todos. Ademas se limitan los fallos por
 * origen, para frenar a quien prueba una clave contra muchos correos.
 */
@Service
@Transactional(noRollbackFor = AuthenticationException.class)
public class LoginService {

	private final AuthenticationManager authenticationManager;
	private final UserRepository users;
	private final LoginPolicy loginPolicy;
	private final LoginThrottle throttle;
	private final Clock clock;

	LoginService(AuthenticationManager authenticationManager, UserRepository users, LoginPolicy loginPolicy,
			LoginThrottle throttle, Clock clock) {
		this.authenticationManager = authenticationManager;
		this.users = users;
		this.loginPolicy = loginPolicy;
		this.throttle = throttle;
		this.clock = clock;
	}

	/** {@code client} identifica el origen del intento (direccion del cliente). */
	public Authentication login(String email, String password, String client) {
		throttle.requireAllowed(client);
		String normalizedEmail = Email.normalize(email);
		try {
			Authentication authentication = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
			users.clearFailedLogins(((AuthenticatedPrincipal) authentication.getPrincipal()).id());
			return authentication;
		}
		catch (AuthenticationException ex) {
			users.registerFailedLogin(normalizedEmail, Instant.now(clock), loginPolicy);
			throttle.registerFailure(client);
			throw ex;
		}
	}
}
