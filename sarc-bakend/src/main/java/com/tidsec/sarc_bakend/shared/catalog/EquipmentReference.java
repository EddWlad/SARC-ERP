package com.tidsec.sarc_bakend.shared.catalog;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Lo que otros modulos pueden saber de un equipo del inventario: su identidad estable y su
 * clasificacion oficial (PRD RF-003: la red de una sesion sale del equipo, no del archivo).
 */
public record EquipmentReference(UUID id, ChargingNetwork network, InfrastructureType type, String name,
		String assignedCode) {
}
