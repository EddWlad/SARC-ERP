import { Component, input } from '@angular/core';

import {
  CLASSIFICATION_LABELS,
  REASON_LABELS,
  STATE_BADGES,
  STATE_LABELS,
} from '../../domain/intermittency-labels';
import { IntermittencySummary } from '../../domain/intermittency-summary';
import { formatInstant } from '../../domain/intermittency-time';
import { hoursText } from '../../domain/intermittency-summary';

type OutsideDetailsData = NonNullable<IntermittencySummary['outsideOfficial']['details']>;

/**
 * Detalle de lo que esta fuera de los indicadores oficiales: ventanas abiertas, por revisar y de prueba, y
 * filas pendientes de lotes confirmados. Solo lo reciben los editores (FR-005); el servidor lo acota.
 */
@Component({
  selector: 'sarc-outside-details',
  template: `
    @if (details().windows.length > 0 || details().pendingRows.length > 0) {
      <details class="detail">
        <summary>
          Ver el detalle ({{ details().windows.length + details().pendingRows.length }})
        </summary>
        <ul class="detail-list">
          @for (window of details().windows; track window.windowId) {
            <li>
              <span [class]="badges[window.state]">{{ states[window.state] }}</span>
              <strong>{{ window.pointLabel }}</strong>
              <span class="muted">
                {{ clock(window.startAt) }} → {{ window.endAt ? clock(window.endAt) : 'abierta' }} ·
                {{ hours(window.minutes) }}{{ window.endAt ? '' : ' provisional' }}
                @for (reason of window.reasons; track reason) {
                  · {{ reasons[reason] }}
                }
              </span>
            </li>
          }
          @for (row of details().pendingRows; track row.batchId + '-' + row.rowNumber) {
            <li>
              <span class="badge badge-warn">{{ classifications[row.classification] }}</span>
              <strong>Fila {{ row.rowNumber }} de un lote confirmado</strong>
            </li>
          }
        </ul>
        @if (details().truncated) {
          <p class="muted">Se muestran los primeros 100; el resto está en Revisión.</p>
        }
      </details>
    }
  `,
  styles: `
    @use '../../../../shared/ui/badges';

    :host {
      display: block;
    }

    .detail {
      margin: var(--sarc-space-3) 0;
      padding: var(--sarc-space-3) var(--sarc-space-4);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-surface);

      summary {
        cursor: pointer;
        font: var(--mat-sys-title-small);
      }
    }

    .detail-list {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      margin: var(--sarc-space-3) 0 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--sarc-space-1) var(--sarc-space-3);
        padding-bottom: var(--sarc-space-2);
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        overflow-wrap: anywhere;
      }
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class OutsideDetails {
  readonly details = input.required<OutsideDetailsData>();

  protected readonly badges = STATE_BADGES;
  protected readonly states = STATE_LABELS;
  protected readonly reasons = REASON_LABELS;
  protected readonly classifications = CLASSIFICATION_LABELS;
  protected readonly hours = hoursText;

  protected clock(instant: string): string {
    return formatInstant(instant);
  }
}
