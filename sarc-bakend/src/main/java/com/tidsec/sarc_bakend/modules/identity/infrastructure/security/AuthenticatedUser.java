package com.tidsec.sarc_bakend.modules.identity.infrastructure.security;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.shared.security.AuthenticatedPrincipal;
import com.tidsec.sarc_bakend.shared.security.SecurityAuthorities;

/**
 * Principal guardado en la sesion. {@code getUsername()} devuelve el id y no el correo: es el nombre de
 * principal que indexa Spring Session, y el id es estable y cabe en su columna. Mientras la clave sea
 * temporal solo concede {@link SecurityAuthorities#PASSWORD_CHANGE_REQUIRED}, nunca los roles.
 */
final class AuthenticatedUser implements UserDetails, CredentialsContainer, AuthenticatedPrincipal {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID id;
	private final Set<Role> roles;
	private final boolean mustChangePassword;
	private final boolean enabled;
	private final boolean accountNonLocked;
	private String passwordHash;

	private AuthenticatedUser(UserAccount user, Instant now) {
		this.id = user.getId();
		this.roles = Set.copyOf(user.getRoles());
		this.mustChangePassword = user.isMustChangePassword();
		this.enabled = user.isActive();
		this.accountNonLocked = !user.isLocked(now);
		this.passwordHash = user.getPasswordHash();
	}

	static AuthenticatedUser from(UserAccount user, Instant now) {
		return new AuthenticatedUser(user, now);
	}

	@Override
	public UUID id() {
		return id;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (mustChangePassword) {
			return List.of(new SimpleGrantedAuthority(SecurityAuthorities.PASSWORD_CHANGE_REQUIRED));
		}
		return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return id.toString();
	}

	@Override
	public boolean isAccountNonLocked() {
		return accountNonLocked;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}
}
