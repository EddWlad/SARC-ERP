package com.tidsec.sarc_bakend.modules.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.tidsec.sarc_bakend.modules.identity.application.command.CreateUserCommand;
import com.tidsec.sarc_bakend.modules.identity.application.command.UpdateUserCommand;
import com.tidsec.sarc_bakend.modules.identity.application.port.UserSessionRegistry;
import com.tidsec.sarc_bakend.modules.identity.application.result.CreatedUser;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

	@Mock UserRepository users;
	@Mock PasswordEncoder passwordEncoder;
	@Mock UserSessionRegistry sessions;
	@Mock AuditTrail audit;
	@Mock CurrentActor currentActor;

	private UserManagementService service;
	private UserAccount existing;

	@BeforeEach
	void setUp() {
		service = new UserManagementService(users, passwordEncoder, new TemporaryPasswordGenerator(), sessions,
				audit, currentActor);
		existing = UserAccount.create(new Email("ana@eos.com"), "Ana", Set.of(Role.ANALISTA), "hash");
	}

	@Test
	void createStoresOnlyTheHashAndReturnsTheTemporaryPasswordOnce() {
		when(users.existsByEmail("nuevo@eos.com")).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("encoded");
		when(users.save(any())).thenAnswer(call -> call.getArgument(0));

		CreatedUser created = service.create(new CreateUserCommand("Nuevo@EOS.com", "Nuevo", Set.of(Role.GERENTE)));

		ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
		verify(passwordEncoder).encode(plain.capture());
		assertThat(plain.getValue()).isEqualTo(created.temporaryPassword()).hasSize(16);
		assertThat(created.user().getPasswordHash()).isEqualTo("encoded");
		assertThat(created.user().isMustChangePassword()).isTrue();
		verify(audit).record(eq("USER_CREATED"), eq("USER"), eq(created.user().getId()), any());
	}

	@Test
	void createRejectsDuplicatedEmail() {
		when(users.existsByEmail("ana@eos.com")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new CreateUserCommand("ANA@eos.com", "Otra", Set.of(Role.ANALISTA))))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("EMAIL_ALREADY_EXISTS");
		verify(users, never()).save(any());
	}

	@Test
	void updateWithStaleVersionIsRejectedWithoutChanges() {
		when(users.findById(existing.getId())).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.update(new UpdateUserCommand(existing.getId(), 7L, "Nuevo nombre", null, null)))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("CONCURRENT_MODIFICATION");
		assertThat(existing.getFullName()).isEqualTo("Ana");
		verifyNoInteractions(sessions);
	}

	@Test
	void changingRolesAuditsAndRevokesSessions() {
		storedWithVersionZero();

		service.update(new UpdateUserCommand(existing.getId(), 0L, null, Set.of(Role.SUPERVISOR), null));

		assertThat(existing.getRoles()).containsExactly(Role.SUPERVISOR);
		verify(audit).record(eq("USER_ROLES_CHANGED"), eq("USER"), eq(existing.getId()), any());
		verify(sessions).invalidateAll(existing.getId());
	}

	@Test
	void renamingAloneKeepsSessions() {
		storedWithVersionZero();

		service.update(new UpdateUserCommand(existing.getId(), 0L, "Ana Maria", Set.of(Role.ANALISTA), null));

		assertThat(existing.getFullName()).isEqualTo("Ana Maria");
		verifyNoInteractions(sessions);
		verifyNoInteractions(audit);
	}

	@Test
	void deactivatingRevokesSessions() {
		storedWithVersionZero();

		service.update(new UpdateUserCommand(existing.getId(), 0L, null, null, UserStatus.INACTIVE));

		assertThat(existing.isActive()).isFalse();
		verify(audit).record(eq("USER_DEACTIVATED"), eq("USER"), eq(existing.getId()), any());
		verify(sessions).invalidateAll(existing.getId());
	}

	@Test
	void administratorCannotDeactivateOwnAccount() {
		storedWithVersionZero();
		when(currentActor.id()).thenReturn(Optional.of(existing.getId()));

		assertThatThrownBy(() -> service.update(new UpdateUserCommand(existing.getId(), 0L, null, null,
				UserStatus.INACTIVE)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("CANNOT_DEACTIVATE_SELF");
	}

	@Test
	void administratorCannotDropOwnAdminRole() {
		UserAccount admin = UserAccount.create(new Email("admin@eos.com"), "Admin",
				Set.of(Role.ADMINISTRADOR, Role.ANALISTA), "hash");
		markPersisted(admin);
		when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
		when(currentActor.id()).thenReturn(Optional.of(admin.getId()));

		assertThatThrownBy(() -> service.update(new UpdateUserCommand(admin.getId(), 0L,
				null, Set.of(Role.ANALISTA), null)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("CANNOT_REMOVE_OWN_ADMIN_ROLE");
	}

	@Test
	void resetPasswordRevokesSessionsAndForcesChange() {
		when(users.findById(existing.getId())).thenReturn(Optional.of(existing));
		when(passwordEncoder.encode(any())).thenReturn("temp-hash");

		String temporary = service.resetPassword(existing.getId());

		assertThat(temporary).hasSize(16);
		assertThat(existing.getPasswordHash()).isEqualTo("temp-hash");
		assertThat(existing.isMustChangePassword()).isTrue();
		verify(sessions).invalidateAll(existing.getId());
	}

	@Test
	void unknownUserIsNotFound() {
		UUID id = UUID.randomUUID();
		when(users.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.resetPassword(id)).isInstanceOf(NotFoundException.class);
	}

	private void storedWithVersionZero() {
		markPersisted(existing);
		when(users.findById(existing.getId())).thenReturn(Optional.of(existing));
	}

	/** Una entidad nueva no tiene version hasta persistirse; se simula la lectura de una fila existente. */
	private static void markPersisted(UserAccount user) {
		ReflectionTestUtils.setField(user, "version", 0L);
	}
}
