package com.tidsec.sarc_bakend.modules.identity.application.service;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.identity.application.command.CreateUserCommand;
import com.tidsec.sarc_bakend.modules.identity.application.command.UpdateUserCommand;
import com.tidsec.sarc_bakend.modules.identity.application.port.UserSessionRegistry;
import com.tidsec.sarc_bakend.modules.identity.application.result.CreatedUser;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserSearchCriteria;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/** Casos de uso administrativos de RF-001. Solo el rol ADMINISTRADOR puede ejecutarlos. */
@Service
@Transactional
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UserManagementService {

	static final String ENTITY_TYPE = "USER";

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator passwordGenerator;
	private final UserSessionRegistry sessions;
	private final AuditTrail audit;
	private final CurrentActor currentActor;

	UserManagementService(UserRepository users, PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator passwordGenerator, UserSessionRegistry sessions, AuditTrail audit,
			CurrentActor currentActor) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.passwordGenerator = passwordGenerator;
		this.sessions = sessions;
		this.audit = audit;
		this.currentActor = currentActor;
	}

	public CreatedUser create(CreateUserCommand command) {
		Email email = new Email(command.email());
		if (users.existsByEmail(email.value())) {
			throw new ConflictException("EMAIL_ALREADY_EXISTS", "Ya existe un usuario con ese correo");
		}
		String temporaryPassword = passwordGenerator.generate();
		UserAccount user = users.save(UserAccount.create(email, command.fullName(), command.roles(),
				passwordEncoder.encode(temporaryPassword)));
		audit.record("USER_CREATED", ENTITY_TYPE, user.getId(),
				Map.of("email", user.getEmail(), "roles", describe(user.getRoles())));
		return new CreatedUser(user, temporaryPassword);
	}

	public UserAccount update(UpdateUserCommand command) {
		UserAccount user = load(command.id());
		if (!Long.valueOf(command.version()).equals(user.getVersion())) {
			throw new ConflictException("CONCURRENT_MODIFICATION",
					"El usuario fue modificado por otro administrador; recargue e intente de nuevo");
		}
		if (command.fullName() != null) {
			user.rename(command.fullName());
		}
		boolean revokeSessions = false;
		if (command.roles() != null && !command.roles().equals(user.getRoles())) {
			changeRoles(user, command.roles());
			revokeSessions = true;
		}
		if (command.status() != null && command.status() != user.getStatus()) {
			changeStatus(user, command.status());
			revokeSessions = true;
		}
		users.save(user);
		if (revokeSessions) {
			sessions.invalidateAll(user.getId());
		}
		return user;
	}

	public String resetPassword(UUID id) {
		UserAccount user = load(id);
		String temporaryPassword = passwordGenerator.generate();
		user.resetPassword(passwordEncoder.encode(temporaryPassword));
		users.save(user);
		audit.record("PASSWORD_RESET", ENTITY_TYPE, user.getId(), Map.of());
		sessions.invalidateAll(user.getId());
		return temporaryPassword;
	}

	@Transactional(readOnly = true)
	public PageResult<UserAccount> search(UserSearchCriteria criteria, PageQuery pageQuery) {
		return users.search(criteria, pageQuery);
	}

	private void changeRoles(UserAccount user, Set<Role> newRoles) {
		if (isSelf(user) && !newRoles.contains(Role.ADMINISTRADOR)) {
			throw new BusinessRuleException("CANNOT_REMOVE_OWN_ADMIN_ROLE",
					"No puede quitarse a si mismo el rol de administrador");
		}
		String previous = describe(user.getRoles());
		user.replaceRoles(newRoles);
		audit.record("USER_ROLES_CHANGED", ENTITY_TYPE, user.getId(),
				Map.of("from", previous, "to", describe(user.getRoles())));
	}

	private void changeStatus(UserAccount user, UserStatus newStatus) {
		if (newStatus == UserStatus.INACTIVE) {
			if (isSelf(user)) {
				throw new BusinessRuleException("CANNOT_DEACTIVATE_SELF", "No puede desactivar su propia cuenta");
			}
			user.deactivate();
			audit.record("USER_DEACTIVATED", ENTITY_TYPE, user.getId(), Map.of());
		}
		else {
			user.activate();
			audit.record("USER_REACTIVATED", ENTITY_TYPE, user.getId(), Map.of());
		}
	}

	private boolean isSelf(UserAccount user) {
		return currentActor.id().filter(user.getId()::equals).isPresent();
	}

	private UserAccount load(UUID id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "El usuario no existe"));
	}

	private static String describe(Set<Role> roles) {
		return roles.stream().map(Role::name).collect(Collectors.toCollection(TreeSet::new)).toString();
	}
}
