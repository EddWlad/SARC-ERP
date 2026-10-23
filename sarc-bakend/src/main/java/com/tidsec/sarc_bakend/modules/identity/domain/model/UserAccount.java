package com.tidsec.sarc_bakend.modules.identity.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

/**
 * Cuenta de acceso con sus roles. Concentra las reglas de RF-001: multirol, baja logica,
 * contrasena temporal con cambio obligatorio y bloqueo temporal por intentos fallidos.
 * Solo usa anotaciones de la especificacion JPA; no depende de Spring.
 */
@Entity
@Table(name = "auth_user")
public class UserAccount extends BaseAuditableEntity {

	@Column(nullable = false, updatable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(nullable = false)
	private boolean mustChangePassword;

	@Column(nullable = false)
	private int failedLoginAttempts;

	private Instant lockedUntil;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "auth_user_role", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role_code", nullable = false, length = 40)
	@Enumerated(EnumType.STRING)
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	protected UserAccount() {
	}

	private UserAccount(Email email, String fullName, Set<Role> roles, String temporaryPasswordHash) {
		super(UUID.randomUUID());
		this.email = email.value();
		this.fullName = requireFullName(fullName);
		this.passwordHash = temporaryPasswordHash;
		this.status = UserStatus.ACTIVE;
		this.mustChangePassword = true;
		replaceRoles(roles);
	}

	/** Alta administrativa: la cuenta nace activa y con contrasena temporal de cambio obligatorio. */
	public static UserAccount create(Email email, String fullName, Set<Role> roles, String temporaryPasswordHash) {
		return new UserAccount(email, fullName, roles, temporaryPasswordHash);
	}

	public void rename(String newFullName) {
		this.fullName = requireFullName(newFullName);
	}

	public void replaceRoles(Set<Role> newRoles) {
		if (newRoles == null || newRoles.isEmpty()) {
			throw new BusinessRuleException("ROLES_REQUIRED", "El usuario debe tener al menos un rol");
		}
		this.roles.clear();
		this.roles.addAll(newRoles);
	}

	public void deactivate() {
		this.status = UserStatus.INACTIVE;
	}

	public void activate() {
		this.status = UserStatus.ACTIVE;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	public boolean hasRole(Role role) {
		return roles.contains(role);
	}

	public void registerFailedLogin(Instant now, LoginPolicy policy) {
		if (isLocked(now)) {
			return;
		}
		failedLoginAttempts++;
		if (failedLoginAttempts >= policy.maxFailedAttempts()) {
			lockedUntil = now.plus(policy.lockoutDuration());
			failedLoginAttempts = 0;
		}
	}

	public void registerSuccessfulLogin() {
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	/** El propio usuario define su clave: deja de ser temporal. */
	public void changePassword(String newPasswordHash) {
		applyPassword(newPasswordHash, false);
	}

	/** El administrador asigna una clave temporal: se exige cambiarla y se levanta cualquier bloqueo. */
	public void resetPassword(String temporaryPasswordHash) {
		applyPassword(temporaryPasswordHash, true);
	}

	private void applyPassword(String hash, boolean temporary) {
		this.passwordHash = hash;
		this.mustChangePassword = temporary;
		registerSuccessfulLogin();
	}

	private static String requireFullName(String fullName) {
		String trimmed = fullName == null ? "" : fullName.strip();
		if (trimmed.isEmpty() || trimmed.length() > 150) {
			throw new BusinessRuleException("INVALID_FULL_NAME", "El nombre es obligatorio (maximo 150 caracteres)");
		}
		return trimmed;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public UserStatus getStatus() {
		return status;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Set<Role> getRoles() {
		return Set.copyOf(roles);
	}
}
