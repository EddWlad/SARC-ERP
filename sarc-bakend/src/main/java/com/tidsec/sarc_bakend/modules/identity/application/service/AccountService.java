package com.tidsec.sarc_bakend.modules.identity.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.identity.application.port.UserSessionRegistry;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.PasswordPolicy;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;

/** Operaciones del propio usuario autenticado sobre su cuenta. */
@Service
public class AccountService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final UserSessionRegistry sessions;
	private final AuditTrail audit;
	private final LoginPolicy loginPolicy;
	private final Clock clock;

	AccountService(UserRepository users, PasswordEncoder passwordEncoder, UserSessionRegistry sessions,
			AuditTrail audit, LoginPolicy loginPolicy, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.audit = audit;
		this.loginPolicy = loginPolicy;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public UserAccount findAccount(UUID userId) {
		return users.findById(userId)
				.orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "El usuario no existe"));
	}

	/**
	 * Cambia la contrasena y revoca todas las sesiones del usuario, la actual incluida: debe volver a
	 * ingresar. Las claves actuales erroneas cuentan como intentos fallidos para frenar la fuerza bruta
	 * desde una sesion robada, por eso el fallo no revierte la transaccion.
	 */
	@Transactional(noRollbackFor = BusinessRuleException.class)
	public void changePassword(UUID userId, String currentPassword, String newPassword) {
		UserAccount user = findAccount(userId);
		Instant now = Instant.now(clock);
		if (user.isLocked(now) || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			users.registerFailedLogin(user.getEmail(), now, loginPolicy);
			throw new BusinessRuleException("CURRENT_PASSWORD_INCORRECT", "La contrasena actual no es correcta");
		}
		PasswordPolicy.requireValid(newPassword);
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessRuleException("PASSWORD_UNCHANGED", "La nueva contrasena debe ser distinta de la actual");
		}
		user.changePassword(passwordEncoder.encode(newPassword));
		users.save(user);
		audit.record("PASSWORD_CHANGED", UserAccount.AUDIT_ENTITY_TYPE, user.getId(), Map.of());
		sessions.invalidateAll(user.getId());
	}
}
