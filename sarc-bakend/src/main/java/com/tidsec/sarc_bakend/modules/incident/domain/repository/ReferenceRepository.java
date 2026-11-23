package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;

/** Puerto de lectura de las versiones de referencias de tiempo. No se escribe desde la aplicacion: son inmutables. */
public interface ReferenceRepository {

	/** Todas las versiones, de la mas reciente a la mas antigua por fecha de vigencia. */
	List<ReferenceVersion> findVersionsNewestFirst();

	List<ReferenceRule> findRules(UUID versionId);

	java.util.Optional<ReferenceVersion> findVersion(UUID versionId);
}
