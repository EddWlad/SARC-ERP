package com.tidsec.sarc_bakend.modules.identity.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de cuentas; lo implementa la capa de infraestructura. */
public interface UserRepository {

	UserAccount save(UserAccount user);

	Optional<UserAccount> findById(UUID id);

	Optional<UserAccount> findByEmail(String normalizedEmail);

	boolean existsByEmail(String normalizedEmail);

	boolean existsAny();

	PageResult<UserAccount> search(UserSearchCriteria criteria, PageQuery pageQuery);

	/**
	 * Suma un intento fallido de forma atomica (seguro ante intentos en paralelo). Al llegar al
	 * maximo de la politica bloquea la cuenta y reinicia el contador; mientras esta bloqueada no
	 * cuenta. No modifica la version ni los campos de auditoria de la cuenta.
	 */
	void registerFailedLogin(String normalizedEmail, Instant now, LoginPolicy policy);

	/** Tras un ingreso correcto: borra intentos y bloqueo, con las mismas garantias. */
	void clearFailedLogins(UUID userId);
}
