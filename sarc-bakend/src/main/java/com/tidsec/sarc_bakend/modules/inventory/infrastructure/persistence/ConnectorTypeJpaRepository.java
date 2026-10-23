package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;

interface ConnectorTypeJpaRepository extends JpaRepository<ConnectorType, UUID> {

	boolean existsByCode(String code);
}
