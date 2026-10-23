package com.tidsec.sarc_bakend.modules.inventory.application.result;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.Connector;

/** Conector activo de un equipo. */
public record ConnectorView(UUID id, int ordinal, UUID connectorTypeId, String connectorTypeCode,
		String connectorTypeName) {

	static ConnectorView from(Connector connector) {
		return new ConnectorView(connector.getId(), connector.getOrdinal(), connector.getConnectorType().getId(),
				connector.getConnectorType().getCode(), connector.getConnectorType().getName());
	}
}
