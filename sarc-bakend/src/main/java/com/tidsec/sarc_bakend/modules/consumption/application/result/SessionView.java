package com.tidsec.sarc_bakend.modules.consumption.application.result;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;

/** Sesion con el equipo del inventario al que pertenece; {@code equipment} es nulo si esta pendiente. */
public record SessionView(EnergySession session, EquipmentReference equipment) {
}
