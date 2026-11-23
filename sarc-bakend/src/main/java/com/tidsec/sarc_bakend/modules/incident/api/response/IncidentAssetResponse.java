package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un equipo del inventario para el formulario. Nunca incluye UID/PUK, URL OCPP ni serie. */
public record IncidentAssetResponse(UUID id, String name, @Schema(nullable = true) String assignedCode,
		ChargingNetwork network, InfrastructureType infrastructureType, @Schema(nullable = true) String location,
		@Schema(nullable = true) String mapsUrl, List<AssetConnector> connectors) {

	public record AssetConnector(UUID id, int position, String typeLabel, boolean active) {
	}

	public static IncidentAssetResponse from(IncidentAsset asset) {
		return new IncidentAssetResponse(asset.id(), asset.name(), asset.assignedCode(), asset.network(),
				asset.infrastructureType(), asset.location(), asset.mapsUrl(),
				asset.connectors().stream().map(c -> new AssetConnector(c.id(), c.position(), c.typeLabel(), c.active()))
						.toList());
	}
}
