package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ExternalPlatform;

/** Puerto de las identidades externas de los equipos. */
public interface ExternalIdentityRepository {

	ExternalIdentity save(ExternalIdentity identity);

	Optional<ExternalIdentity> findById(UUID id);

	List<ExternalIdentity> findByEquipmentId(UUID equipmentId);

	Optional<ExternalIdentity> findByKey(ExternalPlatform platform, String scope, String externalId);

	void delete(ExternalIdentity identity);
}
