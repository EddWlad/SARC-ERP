package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ExternalIdentityRepository;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

@Component
class ExternalIdentityRepositoryAdapter implements ExternalIdentityRepository {

	private final ExternalIdentityJpaRepository jpa;

	ExternalIdentityRepositoryAdapter(ExternalIdentityJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public ExternalIdentity save(ExternalIdentity identity) {
		return jpa.save(identity);
	}

	@Override
	public Optional<ExternalIdentity> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<ExternalIdentity> findByEquipmentId(UUID equipmentId) {
		return jpa.findByEquipmentIdOrderByPlatformAscExternalIdAsc(equipmentId);
	}

	@Override
	public Optional<ExternalIdentity> findByKey(ExternalPlatform platform, String scope, String externalId) {
		return jpa.findByPlatformAndScopeAndExternalId(platform, scope, externalId);
	}

	@Override
	public List<ExternalIdentity> findVerified(ExternalPlatform platform) {
		return jpa.findByPlatformAndVerifiedTrue(platform);
	}

	@Override
	public void delete(ExternalIdentity identity) {
		jpa.delete(identity);
	}
}
