package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.ConnectorAlias;

public record ConnectorAliasResponse(UUID id, UUID equipmentId, UUID connectorId, String label, boolean active,
		long version) {

	public static ConnectorAliasResponse from(ConnectorAlias alias) {
		return new ConnectorAliasResponse(alias.getId(), alias.getEquipmentId(), alias.getConnectorId(),
				alias.getLabelOriginal(), alias.isActive(), alias.getVersion());
	}
}
