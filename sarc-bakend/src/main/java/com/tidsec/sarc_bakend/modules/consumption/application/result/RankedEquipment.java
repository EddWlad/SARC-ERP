package com.tidsec.sarc_bakend.modules.consumption.application.result;

import java.math.BigDecimal;

import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;

/** Posicion de un equipo en el ranking de energia del periodo. */
public record RankedEquipment(EquipmentReference equipment, long sessions, BigDecimal energyKwh) {
}
