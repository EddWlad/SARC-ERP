import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LucidePlus, LucideTrash2 } from '@lucide/angular';

import { WindowResponse } from '../../../../core/api/generated';
import { CaptureFacade, WindowDraft } from '../../application/capture-facade';
import { STATE_LABELS } from '../../domain/intermittency-labels';
import {
  durationText,
  formatDay,
  formatInstant,
  minutesLabel,
  todayInOperatingZone,
} from '../../domain/intermittency-time';
import { OpenWindowsPanel } from '../open-windows-panel/open-windows-panel';
import { PointPicker } from '../point-picker/point-picker';

type TextField = 'start' | 'end' | 'endDate' | 'comment';
type FlagField = 'open' | 'endsNextDay';

/** Captura manual: un punto KIA, un dia y todas las ventanas que se necesiten (FR-019 a FR-021). */
@Component({
  selector: 'sarc-intermittency-capture-page',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    LucidePlus,
    LucideTrash2,
    OpenWindowsPanel,
    PointPicker,
  ],
  providers: [CaptureFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Registrar intermitencias</h1>
        <p>
          Elija el punto y el día, y registre todas las ventanas en que estuvo fuera de línea. No
          hay un límite de ventanas por día.
        </p>
      </div>
    </header>

    <form class="capture" aria-label="Captura de ventanas" (submit)="onSubmit($event, false)">
      <div class="target">
        <sarc-point-picker [point]="facade.point()" (pointChange)="facade.selectPoint($event)" />
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Día</mat-label>
          <input
            matInput
            type="date"
            required
            [max]="today"
            [value]="facade.date()"
            (input)="facade.setDate(valueOf($event))"
          />
        </mat-form-field>
      </div>

      <ol class="windows" aria-label="Ventanas del día">
        @for (row of facade.rowViews(); track row.key; let index = $index) {
          <li class="window" [class.invalid]="row.issue || facade.rowErrors()[row.key]">
            <div class="fields">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Inicio {{ index + 1 }}</mat-label>
                <input
                  matInput
                  type="time"
                  [value]="row.start"
                  (input)="setText(row, 'start', $event)"
                />
              </mat-form-field>
              @if (!row.open) {
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Fin {{ index + 1 }}</mat-label>
                  <input
                    matInput
                    type="time"
                    [value]="row.end"
                    (input)="setText(row, 'end', $event)"
                  />
                </mat-form-field>
              }
              <mat-form-field class="comment" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Comentario (opcional)</mat-label>
                <input
                  matInput
                  maxlength="500"
                  [value]="row.comment"
                  (input)="setText(row, 'comment', $event)"
                />
              </mat-form-field>
              <button
                matIconButton
                type="button"
                class="remove"
                [attr.aria-label]="'Quitar la ventana ' + (index + 1)"
                (click)="facade.removeRow(row.key)"
              >
                <svg lucideTrash2 [size]="18" aria-hidden="true"></svg>
              </button>
            </div>
            <div class="options">
              <mat-checkbox [checked]="row.open" (change)="setFlag(row, 'open', $event.checked)">
                Sin fin (sigue abierta)
              </mat-checkbox>
              @if (!row.open) {
                <mat-checkbox
                  [checked]="row.endsNextDay"
                  [disabled]="row.endDate !== ''"
                  (change)="setFlag(row, 'endsNextDay', $event.checked)"
                >
                  Termina el día siguiente
                </mat-checkbox>
                <mat-form-field class="end-date" appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Fin en otra fecha (opcional)</mat-label>
                  <input
                    matInput
                    type="date"
                    [min]="facade.date()"
                    [max]="today"
                    [value]="row.endDate"
                    (input)="setText(row, 'endDate', $event)"
                  />
                </mat-form-field>
              }
            </div>
            <div class="summary" aria-live="polite">
              @if (row.minutes !== null && row.minutes > 0) {
                <span class="badge badge-info">
                  {{ durationText(row.minutes) }} · {{ minutesLabel(row.minutes) }}
                </span>
              } @else if (row.open && row.start) {
                <span class="badge badge-off">Abierta: se suma al cerrarla</span>
              }
              @if (row.warning) {
                <span class="note">{{ row.warning }}</span>
              }
              @if (row.issue) {
                <span class="issue" role="alert">{{ row.issue }}</span>
              }
              @if (facade.rowErrors()[row.key]; as serverError) {
                <span class="issue" role="alert">{{ serverError }}</span>
              }
            </div>
          </li>
        }
      </ol>

      <button matButton="tonal" type="button" class="add" (click)="facade.addRow()">
        <svg lucidePlus [size]="18" aria-hidden="true"></svg>
        Agregar ventana
      </button>

      @if (facade.dayTotals().length > 0) {
        <section class="totals" aria-label="Total del día">
          <h2>Total del día</h2>
          <ul>
            @for (total of facade.dayTotals(); track total.day) {
              <li>
                <strong>{{ formatDay(total.day) }}</strong>
                {{ durationText(total.minutes) }} · {{ minutesLabel(total.minutes) }}
              </li>
            }
          </ul>
          @if (facade.dayTotals().length > 1) {
            <p class="note">
              Una ventana que cruza la medianoche aporta a cada día solo el tiempo que le
              corresponde.
            </p>
          }
        </section>
      }

      @if (facade.busy()) {
        <mat-progress-bar mode="indeterminate" aria-label="Guardando ventanas" />
      }
      @if (facade.error(); as error) {
        <div class="form-alert" role="alert">
          <p>{{ error }}</p>
        </div>
      }

      <div class="actions">
        <button matButton="outlined" type="submit" [disabled]="!facade.canSave() || facade.busy()">
          Guardar
        </button>
        <button
          matButton="filled"
          type="button"
          [disabled]="!facade.canSave() || facade.busy()"
          (click)="onSubmit($event, true)"
        >
          Guardar y registrar otro punto
        </button>
      </div>
    </form>

    @if (facade.saved(); as saved) {
      <section class="saved" role="status" aria-label="Última captura guardada">
        <h2>Se guardaron {{ saved.windows.length }} ventanas</h2>
        <ul>
          @for (window of saved.windows; track window.id) {
            <li>
              <span class="badge" [class]="badgeOf(window)">{{ stateLabels[window.state] }}</span>
              {{ window.pointLabel }} · {{ clock(window.startAt) }}
              @if (window.endAt) {
                – {{ clock(window.endAt) }}
              }
              @if (window.durationMinutes) {
                · {{ durationText(window.durationMinutes) }}
              } @else if (window.provisionalText) {
                · provisional {{ window.provisionalText }}
              }
            </li>
          }
        </ul>
      </section>
    }

    <sarc-open-windows-panel [point]="facade.point()" [refresh]="facade.savedCount()" />
  `,
  styles: `
    @use '../../../../shared/ui/page-chrome';
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/ui/dialog-form';

    :host {
      display: block;
    }

    .capture,
    .saved {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-4);
      padding: var(--sarc-space-4);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-surface);
    }

    .saved {
      margin-top: var(--sarc-space-4);

      h2 {
        margin: 0;
        font: var(--mat-sys-title-medium);
      }

      ul {
        margin: 0;
        padding: 0;
        list-style: none;
        display: flex;
        flex-direction: column;
        gap: var(--sarc-space-2);
      }
    }

    .target {
      display: grid;
      grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
      gap: var(--sarc-space-3);
      align-items: start;

      @media (max-width: 767.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .windows {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .window {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      padding: var(--sarc-space-3);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-sm);

      &.invalid {
        border-color: var(--mat-sys-error);
      }
    }

    .fields {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 2fr) auto;
      gap: var(--sarc-space-3);
      align-items: start;

      @media (max-width: 767.98px) {
        grid-template-columns: repeat(2, minmax(0, 1fr));

        .comment {
          grid-column: 1 / -1;
        }
      }
    }

    .remove {
      align-self: center;
    }

    .options {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-3);
    }

    .end-date {
      min-width: 220px;
    }

    .summary {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-2) var(--sarc-space-3);
      min-height: 24px;
    }

    .issue {
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-medium);
    }

    .totals {
      h2 {
        margin: 0 0 var(--sarc-space-2);
        font: var(--mat-sys-title-small);
      }

      ul {
        display: flex;
        flex-wrap: wrap;
        gap: var(--sarc-space-2) var(--sarc-space-5);
        margin: 0;
        padding: 0;
        list-style: none;
      }

      strong {
        margin-inline-end: var(--sarc-space-2);
      }
    }

    .add {
      align-self: flex-start;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--sarc-space-2);
    }
  `,
})
export class CapturePage {
  protected readonly facade = inject(CaptureFacade);
  protected readonly today = todayInOperatingZone();
  protected readonly stateLabels = STATE_LABELS;
  protected readonly durationText = durationText;
  protected readonly minutesLabel = minutesLabel;
  protected readonly formatDay = formatDay;

  constructor() {
    this.facade.setDate(this.today);
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected setText(row: WindowDraft, field: TextField, event: Event): void {
    this.facade.updateRow(row.key, { [field]: this.valueOf(event) });
  }

  protected setFlag(row: WindowDraft, field: FlagField, checked: boolean): void {
    this.facade.updateRow(row.key, { [field]: checked });
  }

  protected badgeOf(window: WindowResponse): string {
    switch (window.state) {
      case 'OPEN':
        return 'badge badge-accent';
      case 'REVIEW':
        return 'badge badge-warn';
      default:
        return 'badge';
    }
  }

  protected clock(instant: string): string {
    return formatInstant(instant);
  }

  protected async onSubmit(event: Event, another: boolean): Promise<void> {
    event.preventDefault();
    if (!this.facade.canSave() || this.facade.busy()) return;
    await this.facade.save(another);
  }
}
