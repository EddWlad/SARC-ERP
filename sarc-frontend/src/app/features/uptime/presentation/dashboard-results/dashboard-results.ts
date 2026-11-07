import { Component, inject, input } from '@angular/core';
import { MatPaginatorIntl, MatPaginatorModule } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';

import {
  ConnectorResultResponse,
  EquipmentResultResponse,
  ExclusionResponse,
} from '../../../../core/api/generated';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { clientPage } from '../../domain/client-page';

const STATE_LABELS: Readonly<Record<string, string>> = {
  NO_DATA: 'Sin datos',
  OUT_OF_SCOPE: 'Fuera de alcance',
  RETIRED: 'Retirado',
  EXCLUDED: 'Excluida',
};

/** Ranking por conector, promedio por equipo y exclusiones, en tablas o tarjetas segun el ancho. */
@Component({
  selector: 'sarc-dashboard-results',
  imports: [MatPaginatorModule, MatTableModule],
  providers: [{ provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './dashboard-results.html',
  styleUrl: './dashboard-results.scss',
})
export class DashboardResults {
  readonly connectors = input.required<readonly ConnectorResultResponse[]>();
  readonly equipment = input.required<readonly EquipmentResultResponse[]>();
  readonly exclusions = input.required<readonly ExclusionResponse[]>();
  /** Identifica el bloque cuando la pagina muestra dos: ids y nombres accesibles no se repiten. */
  readonly scope = input('oficial');
  readonly scopeLabel = input('');

  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly stateLabels = STATE_LABELS;
  protected readonly pageSizeOptions = [10, 20, 50];
  protected readonly connectorColumns = [
    'position',
    'equipment',
    'connector',
    'uptime',
    'offline',
    'fault',
  ];
  protected readonly equipmentColumns = ['equipment', 'uptime', 'connectors'];
  protected readonly exclusionColumns = ['equipment', 'connector', 'state'];
  protected readonly connectorPage = clientPage(this.connectors);
  protected readonly equipmentPage = clientPage(this.equipment);
  protected readonly exclusionPage = clientPage(this.exclusions);
}
