package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

public record ConnectorTypeResponse(UUID id, String code, String name, AdministrativeStatus status, long version) {

	public static ConnectorTypeResponse from(ConnectorType type) {
		return new ConnectorTypeResponse(type.getId(), type.getCode(), type.getName(), type.getAdminStatus(),
				type.getVersion());
	}
}
