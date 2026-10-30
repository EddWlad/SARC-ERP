package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

/** Puerto de las identidades externas de los equipos. */
public interface ExternalIdentityRepository {

	ExternalIdentity save(ExternalIdentity identity);

	Optional<ExternalIdentity> findById(UUID id);

	List<ExternalIdentity> findByEquipmentId(UUID equipmentId);

	Optional<ExternalIdentity> findByKey(ExternalPlatform platform, String scope, String externalId);

	/** Identidades verificadas de una plataforma, para conciliar importaciones. */
	List<ExternalIdentity> findVerified(ExternalPlatform platform);

	/** Todas las identidades de una plataforma, para compararlas por su forma normalizada. */
	List<ExternalIdentity> findByPlatform(ExternalPlatform platform);

	void delete(ExternalIdentity identity);
}
