package com.tidsec.sarc_bakend.modules.identity.infrastructure.bootstrap;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.PasswordPolicy;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;

/**
 * Crea el primer administrador cuando no existe ningun usuario y hay credenciales configuradas.
 * Sin esto no habria quien dar de alta al resto. Entra con clave temporal: debe cambiarla al ingresar.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

	private final BootstrapAdminProperties properties;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuditTrail audit;

	BootstrapAdminInitializer(BootstrapAdminProperties properties, UserRepository users,
			PasswordEncoder passwordEncoder, AuditTrail audit) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (properties.isPartiallyConfigured()) {
			throw new IllegalStateException(
					"sarc.bootstrap-admin requiere definir email y password juntos, o ninguno de los dos");
		}
		if (!properties.isConfigured() || users.existsAny()) {
			return;
		}
		PasswordPolicy.requireValid(properties.password());
		UserAccount admin = users.save(UserAccount.create(new Email(properties.email()), properties.fullName(),
				Set.of(Role.ADMINISTRADOR), passwordEncoder.encode(properties.password())));
		audit.record("USER_CREATED", UserAccount.AUDIT_ENTITY_TYPE, admin.getId(),
				Map.of("email", admin.getEmail(), "bootstrap", "true"));
		log.info("Administrador inicial creado: {}", admin.getEmail());
	}
}
