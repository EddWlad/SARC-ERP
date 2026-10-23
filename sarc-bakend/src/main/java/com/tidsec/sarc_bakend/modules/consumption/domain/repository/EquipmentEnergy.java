package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Sesiones y energia de un equipo fisico (por su ID interno, no por alias de plataforma). */
public record EquipmentEnergy(UUID equipmentId, long sessions, BigDecimal energyKwh) {
}
