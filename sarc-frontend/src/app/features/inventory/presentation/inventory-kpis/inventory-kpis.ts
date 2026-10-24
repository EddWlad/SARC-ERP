import { Component, computed, inject, input } from '@angular/core';

import { QuantityFormat } from '../../../../shared/format/quantity-format';
import {
  NETWORK_LABELS,
  NETWORKS,
  USAGE_STATUS_HINTS,
  USAGE_STATUS_LABELS,
  USAGE_STATUSES,
} from '../../domain/equipment';
import { countOf, InventorySummary } from '../../domain/inventory-summary';

interface Kpi {
  readonly label: string;
  readonly value: number;
  readonly hint?: string;
}

/**
 * Indicadores del inventario con los filtros vigentes. Las cifras van en tamano grande y color de
 * marca para leerse de un vistazo; los eliminados se informan aparte porque no suman en los totales.
 */
@Component({
  selector: 'sarc-inventory-kpis',
  templateUrl: './inventory-kpis.html',
  styleUrl: './inventory-kpis.scss',
})
export class InventoryKpis {
  readonly summary = input.required<InventorySummary>();

  private readonly quantities = inject(QuantityFormat);
  protected readonly format = (value: number) => this.quantities.count(value);

  protected readonly headline = computed<readonly Kpi[]>(() => {
    const summary = this.summary();
    return [
      { label: 'Equipos', value: summary.totalEquipment },
      { label: 'Puntos de carga', value: countOf(summary.byType, 'CHARGING_POINT') },
      { label: 'Electrolineras', value: countOf(summary.byType, 'CHARGING_STATION') },
      { label: 'Activos', value: countOf(summary.byAdminStatus, 'ACTIVE') },
      { label: 'Inactivos', value: countOf(summary.byAdminStatus, 'INACTIVE') },
    ];
  });

  protected readonly usage = computed<readonly Kpi[]>(() =>
    USAGE_STATUSES.map((status) => ({
      label: USAGE_STATUS_LABELS[status],
      value: countOf(this.summary().pointsByUsageStatus, status),
      hint: USAGE_STATUS_HINTS[status],
    })),
  );

  protected readonly networks = computed<readonly Kpi[]>(() =>
    NETWORKS.map((network) => ({
      label: NETWORK_LABELS[network],
      value: countOf(this.summary().byNetwork, network),
    })),
  );

  protected readonly connectors = computed<readonly Kpi[]>(() =>
    Object.entries(this.summary().activeConnectorsByType)
      .map(([code, value]) => ({ label: code, value }))
      .sort((a, b) => b.value - a.value || a.label.localeCompare(b.label)),
  );
}
