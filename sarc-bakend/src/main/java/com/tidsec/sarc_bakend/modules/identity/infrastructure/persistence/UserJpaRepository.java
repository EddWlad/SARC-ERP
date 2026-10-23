package com.tidsec.sarc_bakend.modules.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;

interface UserJpaRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

	Optional<UserAccount> findByEmail(String email);

	boolean existsByEmail(String email);
}
