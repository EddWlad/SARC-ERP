import { DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';

import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { DailyEnergy } from '../../domain/consumption';

/** Vista de tabla de las graficas diarias: las mismas cifras sin depender de leer una grafica. */
@Component({
  selector: 'sarc-daily-table',
  imports: [DatePipe],
  template: `
    <details>
      <summary>Ver datos diarios</summary>
      <table>
        <caption class="sarc-visually-hidden">
          Energía y sesiones por día
        </caption>
        <thead>
          <tr>
            <th scope="col">Día</th>
            <th scope="col">Sesiones</th>
            <th scope="col">kWh</th>
          </tr>
        </thead>
        <tbody>
          @for (day of days(); track day.date) {
            <tr>
              <td>{{ day.date | date: 'EEE dd/MM/yyyy' }}</td>
              <td>{{ quantities.count(day.sessions) }}</td>
              <td>{{ quantities.kwh(day.energyKwh) }}</td>
            </tr>
          }
        </tbody>
      </table>
    </details>
  `,
  styles: `
    summary {
      color: var(--mat-sys-primary);
      font: var(--mat-sys-title-small);
      cursor: pointer;
    }

    table {
      width: 100%;
      margin-top: var(--sarc-space-3);
      border-collapse: collapse;
      font: var(--mat-sys-body-medium);
    }

    th,
    td {
      padding: var(--sarc-space-1) var(--sarc-space-2);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      text-align: end;
    }

    th:first-child,
    td:first-child {
      text-align: start;
    }
  `,
})
export class DailyTable {
  readonly days = input.required<readonly DailyEnergy[]>();

  protected readonly quantities = inject(QuantityFormat);
}
