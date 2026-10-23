package com.tidsec.sarc_bakend.modules.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;

interface UserJpaRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

	Optional<UserAccount> findByEmail(String email);

	boolean existsByEmail(String email);

	/** UPDATE masivo: PostgreSQL bloquea la fila y lee el valor vigente; no pasa por version ni auditoria. */
	@Modifying
	@Query("""
			update UserAccount u set
				u.failedLoginAttempts = case when u.failedLoginAttempts + 1 >= :maxAttempts
						then 0 else u.failedLoginAttempts + 1 end,
				u.lockedUntil = case when u.failedLoginAttempts + 1 >= :maxAttempts
						then :lockUntil else u.lockedUntil end
			where u.email = :email and (u.lockedUntil is null or u.lockedUntil <= :now)
			""")
	int registerFailedLogin(String email, int maxAttempts, Instant lockUntil, Instant now);

	@Modifying
	@Query("""
			update UserAccount u set u.failedLoginAttempts = 0, u.lockedUntil = null
			where u.id = :id and (u.failedLoginAttempts > 0 or u.lockedUntil is not null)
			""")
	int clearFailedLogins(UUID id);
}
