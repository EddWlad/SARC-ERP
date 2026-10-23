package com.tidsec.sarc_bakend.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class UserAccountTest {

	private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

	private UserAccount newUser() {
		return UserAccount.create(new Email("  Ana@Example.COM "), " Ana Perez ",
				Set.of(Role.ANALISTA, Role.SUPERVISOR), "hash");
	}

	/** El bloqueo lo escribe el repositorio con una operacion atomica; aqui se simula esa fila. */
	private static void lockUntil(UserAccount user, Instant until) {
		ReflectionTestUtils.setField(user, "lockedUntil", until);
		ReflectionTestUtils.setField(user, "failedLoginAttempts", 2);
	}

	@Test
	void newAccountIsActiveWithTemporaryPasswordAndNormalizedData() {
		UserAccount user = newUser();

		assertThat(user.getEmail()).isEqualTo("ana@example.com");
		assertThat(user.getFullName()).isEqualTo("Ana Perez");
		assertThat(user.isActive()).isTrue();
		assertThat(user.isMustChangePassword()).isTrue();
		assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.ANALISTA, Role.SUPERVISOR);
	}

	@Test
	void accountWithoutRolesIsRejected() {
		assertThatThrownBy(() -> UserAccount.create(new Email("a@b.co"), "Ana", Set.of(), "hash"))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("al menos un rol");
	}

	@Test
	void blankNameIsRejected() {
		assertThatThrownBy(() -> newUser().rename("   ")).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void lockLastsUntilItsEndInstant() {
		UserAccount user = newUser();
		lockUntil(user, NOW.plus(Duration.ofMinutes(15)));

		assertThat(user.isLocked(NOW)).isTrue();
		assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
	}

	@Test
	void adminResetForcesPasswordChangeAndUnlocks() {
		UserAccount user = newUser();
		user.changePassword("own-hash");
		lockUntil(user, NOW.plus(Duration.ofMinutes(15)));

		user.resetPassword("temp-hash");

		assertThat(user.getPasswordHash()).isEqualTo("temp-hash");
		assertThat(user.isMustChangePassword()).isTrue();
		assertThat(user.isLocked(NOW)).isFalse();
	}

	@Test
	void ownPasswordChangeClearsTemporaryFlagAndLock() {
		UserAccount user = newUser();
		lockUntil(user, NOW.plus(Duration.ofMinutes(15)));

		user.changePassword("own-hash");

		assertThat(user.isMustChangePassword()).isFalse();
		assertThat(user.isLocked(NOW)).isFalse();
	}

	@Test
	void deactivateAndActivateToggleStatus() {
		UserAccount user = newUser();

		user.deactivate();
		assertThat(user.isActive()).isFalse();
		user.activate();
		assertThat(user.isActive()).isTrue();
	}
}
