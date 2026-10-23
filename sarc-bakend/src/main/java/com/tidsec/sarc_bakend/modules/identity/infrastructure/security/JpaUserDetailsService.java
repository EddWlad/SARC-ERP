package com.tidsec.sarc_bakend.modules.identity.infrastructure.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;

/** Carga credenciales por correo normalizado para el proveedor de autenticacion de Spring Security. */
@Service
class JpaUserDetailsService implements UserDetailsService {

	private final UserRepository users;
	private final Clock clock;

	JpaUserDetailsService(UserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return users.findByEmail(email)
				.map(user -> AuthenticatedUser.from(user, Instant.now(clock)))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
	}
}
