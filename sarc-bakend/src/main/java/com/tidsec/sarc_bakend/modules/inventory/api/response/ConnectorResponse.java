package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.application.result.ConnectorView;

public record ConnectorResponse(UUID id, int ordinal, UUID connectorTypeId, String connectorTypeCode,
		String connectorTypeName) {

	public static ConnectorResponse from(ConnectorView connector) {
		return new ConnectorResponse(connector.id(), connector.ordinal(), connector.connectorTypeId(),
				connector.connectorTypeCode(), connector.connectorTypeName());
	}
}
