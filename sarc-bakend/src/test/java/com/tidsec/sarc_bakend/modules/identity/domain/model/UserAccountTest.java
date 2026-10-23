package com.tidsec.sarc_bakend.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class UserAccountTest {

	private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
	private static final LoginPolicy POLICY = new LoginPolicy(3, Duration.ofMinutes(15));

	private UserAccount newUser() {
		return UserAccount.create(new Email("  Ana@Example.COM "), " Ana Perez ",
				Set.of(Role.ANALISTA, Role.SUPERVISOR), "hash");
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
	void locksAfterMaxFailedAttemptsAndUnlocksWhenTimePasses() {
		UserAccount user = newUser();

		user.registerFailedLogin(NOW, POLICY);
		user.registerFailedLogin(NOW, POLICY);
		assertThat(user.isLocked(NOW)).isFalse();
		user.registerFailedLogin(NOW, POLICY);

		assertThat(user.isLocked(NOW)).isTrue();
		assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
	}

	@Test
	void failuresWhileLockedDoNotExtendTheLock() {
		UserAccount user = newUser();
		for (int i = 0; i < 3; i++) {
			user.registerFailedLogin(NOW, POLICY);
		}
		Instant lockedUntil = user.getLockedUntil();

		user.registerFailedLogin(NOW.plusSeconds(60), POLICY);

		assertThat(user.getLockedUntil()).isEqualTo(lockedUntil);
	}

	@Test
	void successfulLoginClearsCounterAndLock() {
		UserAccount user = newUser();
		user.registerFailedLogin(NOW, POLICY);
		user.registerFailedLogin(NOW, POLICY);

		user.registerSuccessfulLogin();
		user.registerFailedLogin(NOW, POLICY);
		user.registerFailedLogin(NOW, POLICY);

		assertThat(user.isLocked(NOW)).isFalse();
	}

	@Test
	void adminResetForcesPasswordChangeAndUnlocks() {
		UserAccount user = newUser();
		user.changePassword("own-hash");
		for (int i = 0; i < 3; i++) {
			user.registerFailedLogin(NOW, POLICY);
		}

		user.resetPassword("temp-hash");

		assertThat(user.getPasswordHash()).isEqualTo("temp-hash");
		assertThat(user.isMustChangePassword()).isTrue();
		assertThat(user.isLocked(NOW)).isFalse();
	}

	@Test
	void ownPasswordChangeClearsTemporaryFlag() {
		UserAccount user = newUser();

		user.changePassword("own-hash");

		assertThat(user.isMustChangePassword()).isFalse();
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
