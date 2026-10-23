package com.tidsec.sarc_bakend.modules.identity.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
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
}
