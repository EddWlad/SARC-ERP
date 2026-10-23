package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

interface ExternalIdentityJpaRepository extends JpaRepository<ExternalIdentity, UUID> {

	List<ExternalIdentity> findByEquipmentIdOrderByPlatformAscExternalIdAsc(UUID equipmentId);

	Optional<ExternalIdentity> findByPlatformAndScopeAndExternalId(ExternalPlatform platform, String scope,
			String externalId);

	List<ExternalIdentity> findByPlatformAndVerifiedTrue(ExternalPlatform platform);
}
