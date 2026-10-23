package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ConnectorTypeRepository;

@Component
class ConnectorTypeRepositoryAdapter implements ConnectorTypeRepository {

	private final ConnectorTypeJpaRepository jpa;

	ConnectorTypeRepositoryAdapter(ConnectorTypeJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public ConnectorType save(ConnectorType connectorType) {
		return jpa.save(connectorType);
	}

	@Override
	public Optional<ConnectorType> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<ConnectorType> findAllById(Collection<UUID> ids) {
		return jpa.findAllById(ids);
	}

	@Override
	public boolean existsByCode(String code) {
		return jpa.existsByCode(code);
	}

	@Override
	public List<ConnectorType> findAll() {
		return jpa.findAll(Sort.by("code"));
	}
}
