package com.tidsec.sarc_bakend.shared.catalog;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Lo que otros modulos pueden saber de un conector y de su equipo para medirlo. Nunca incluye UID/PUK
 * ni URL OCPP. La marca viaja como texto porque su tipo es propio del inventario.
 */
public record MeasurableConnector(UUID connectorId, int ordinal, String connectorTypeCode,
		String connectorTypeName, boolean connectorActive, UUID equipmentId, String equipmentName,
		String assignedCode, ChargingNetwork network, InfrastructureType type, String brand,
		AdministrativeStatus equipmentStatus) {
}
