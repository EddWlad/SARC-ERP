import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { WindowDetailResponse } from '../../../../core/api/generated';
import {
  REASON_LABELS,
  revisionChangeLabel,
  SOURCE_LABELS,
  STATE_BADGES,
  STATE_LABELS,
} from '../../domain/intermittency-labels';
import { durationText, formatInstant, present } from '../../domain/intermittency-time';

export interface WindowDetailDialogData {
  readonly detail: WindowDetailResponse;
}

/**
 * Detalle de una ventana: instantes completos, duracion, origen y, solo para editores, el historial de
 * revisiones con quien cambio que y por que (FR-017). El servidor omite las revisiones al resto de los roles.
 */
@Component({
  selector: 'sarc-window-detail-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Ventana de {{ window.pointLabel }}</h2>
    <mat-dialog-content>
      <dl class="facts">
        <div>
          <dt>Estado</dt>
          <dd>
            <span [class]="badges[window.state]">{{ stateLabels[window.state] }}</span>
            @for (reason of window.reviewReasons; track reason) {
              <span class="muted">{{ reasons[reason] }}</span>
            }
          </dd>
        </div>
        <div>
          <dt>Inicio</dt>
          <dd>{{ clock(window.startAt) }}</dd>
        </div>
        <div>
          <dt>Fin</dt>
          <dd>{{ window.endAt ? clock(window.endAt) : 'Abierta' }}</dd>
        </div>
        <div>
          <dt>{{ window.endAt ? 'Duración' : 'Duración provisional' }}</dt>
          <dd>{{ duration() }}</dd>
        </div>
        <div>
          <dt>Origen</dt>
          <dd>
            {{ sources[window.source] }}
            @if (present(detail.rowNumber)) {
              · fila {{ detail.rowNumber
              }}{{ present(detail.pairNumber) ? ', par ' + detail.pairNumber : '' }}
            }
          </dd>
        </div>
        @if (window.comment) {
          <div>
            <dt>Comentario</dt>
            <dd>{{ window.comment }}</dd>
          </div>
        }
        @if (detail.currentPointName && detail.currentPointName !== window.pointLabel) {
          <div>
            <dt>Nombre actual del punto</dt>
            <dd>{{ detail.currentPointName }}</dd>
          </div>
        }
      </dl>

      @if (detail.revisions; as revisions) {
        <h3>Historial</h3>
        <ol class="history" aria-label="Historial de revisiones">
          @for (revision of revisions; track revision.revision) {
            <li>
              <p class="head">
                <strong>{{ changeLabel(revision.change) }}</strong>
                <span class="muted"
                  >Revisión {{ revision.revision }} · {{ clock(revision.at) }}</span
                >
              </p>
              <p class="muted">
                {{ clock(revision.startAt) }} →
                {{ revision.endAt ? clock(revision.endAt) : 'abierta' }}
                @if (present(revision.durationMinutes)) {
                  · {{ minutes(revision.durationMinutes) }}
                }
                · {{ stateLabels[revision.state] }}
              </p>
              @if (revision.comment) {
                <p class="muted">Comentario: {{ revision.comment }}</p>
              }
              @if (revision.justification) {
                <p>Justificación: {{ revision.justification }}</p>
              }
            </li>
          }
        </ol>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton="filled" type="button" mat-dialog-close>Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: `
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/ui/dialog-form';

    :host {
      display: block;
      min-width: min(520px, 75vw);
    }

    .facts {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3);
      margin: 0 0 var(--sarc-space-4);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }

      dt {
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-label-medium);
      }

      dd {
        display: flex;
        flex-wrap: wrap;
        gap: var(--sarc-space-1) var(--sarc-space-2);
        margin: 0;
        overflow-wrap: anywhere;
      }
    }

    h3 {
      margin: 0 0 var(--sarc-space-2);
      font: var(--mat-sys-title-small);
    }

    .history {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        padding-bottom: var(--sarc-space-3);
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }

      p {
        margin: 0;
      }
    }

    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: var(--sarc-space-1) var(--sarc-space-3);
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class WindowDetailDialog {
  private readonly data = inject<WindowDetailDialogData>(MAT_DIALOG_DATA);

  protected readonly detail = this.data.detail;
  protected readonly window = this.data.detail.window;
  protected readonly badges = STATE_BADGES;
  protected readonly stateLabels = STATE_LABELS;
  protected readonly sources = SOURCE_LABELS;
  protected readonly reasons = REASON_LABELS;
  protected readonly present = present;

  protected clock(instant: string): string {
    return formatInstant(instant);
  }

  protected changeLabel(code: string): string {
    return revisionChangeLabel(code);
  }

  protected minutes(minutes: number): string {
    return `${durationText(minutes)} (${minutes} min)`;
  }

  protected duration(): string {
    const minutes = this.window.endAt
      ? this.window.durationMinutes
      : this.window.provisionalMinutes;
    return present(minutes) ? this.minutes(minutes) : '—';
  }
}
