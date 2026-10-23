package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;

/** Puerto del catalogo de tipos de conector. */
public interface ConnectorTypeRepository {

	ConnectorType save(ConnectorType connectorType);

	Optional<ConnectorType> findById(UUID id);

	List<ConnectorType> findAllById(Collection<UUID> ids);

	boolean existsByCode(String code);

	/** Catalogo completo ordenado por codigo. */
	List<ConnectorType> findAll();
}
