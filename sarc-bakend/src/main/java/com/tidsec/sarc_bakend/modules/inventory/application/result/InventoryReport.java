package com.tidsec.sarc_bakend.modules.inventory.application.result;

import java.time.Instant;
import java.util.List;

/** Datos de un reporte de inventario: alcance (filtros), fecha de corte, indicadores y detalle. */
public record InventoryReport(String scope, Instant cutOff, InventorySummary summary, List<EquipmentView> equipment) {
}
