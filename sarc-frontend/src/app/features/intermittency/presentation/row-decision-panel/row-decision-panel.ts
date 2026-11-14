import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';

import { ImportRowDecisionRequest, KiaPointResponse } from '../../../../core/api/generated';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { ImportRowDialogData } from '../../application/import-row-actions';
import {
  IntermittencyImportRow,
  RowDecision,
  RowWindow,
  WINDOW_RESOLUTION_LABELS,
  WindowResolution,
} from '../../domain/intermittency-import';
import { CLASSIFICATION_LABELS, issueText } from '../../domain/intermittency-labels';
import {
  durationText,
  formatInstant,
  present,
  sourceCellText,
} from '../../domain/intermittency-time';
import { PointPicker } from '../point-picker/point-picker';

/** Una resolucion que se puede elegir para una ventana; deshabilitada si algo la impide, con el motivo. */
export interface ResolutionOption {
  readonly value: WindowResolution;
  readonly label: string;
  readonly disabledReason?: string;
}

/** Por que una ventana de prueba no se puede dejar abierta (FR-016). */
export const OPEN_TEST_REASON = 'Una ventana de prueba debe estar cerrada';

/** Las resoluciones que corresponden a una ventana segun sus observaciones (FR-027 a FR-029). */
export function resolutionOptions(
  window: RowWindow,
  row: IntermittencyImportRow,
): ResolutionOption[] {
  const codes = window.issues.map((issue) => issue.code);
  const decision = window.decision ?? null;
  const options: ResolutionOption[] = [];
  const stored = codes.includes('OVERLAPS_EXISTING') || decision === 'KEEP_EXISTING';
  if (stored) {
    options.push({ value: 'KEEP_EXISTING', label: WINDOW_RESOLUTION_LABELS.KEEP_EXISTING });
    if (window.conflictWindowId || decision === 'KEEP_EXISTING' || decision === 'REPLACE') {
      options.push({ value: 'REPLACE', label: WINDOW_RESOLUTION_LABELS.REPLACE });
    }
  }
  const overlapping = row.interpretation.windows.filter((other) =>
    other.issues.some((issue) => issue.code === 'OVERLAP_IN_BATCH'),
  );
  if ((codes.includes('OVERLAP_IN_BATCH') && overlapping.length >= 2) || decision === 'MERGE') {
    options.push({ value: 'MERGE', label: WINDOW_RESOLUTION_LABELS.MERGE });
  }
  if ((codes.includes('INCOMPLETE_PAIR') || decision === 'OPEN') && window.startAt) {
    options.push({
      value: 'OPEN',
      label: WINDOW_RESOLUTION_LABELS.OPEN,
      ...(window.test ? { disabledReason: OPEN_TEST_REASON } : {}),
    });
  }
  options.push({ value: 'OMIT', label: WINDOW_RESOLUTION_LABELS.OMIT });
  return options;
}

interface WindowChoice {
  resolution: WindowResolution | '';
  justification: string;
}

/**
 * Decide una fila de la vista previa: vincularla a un punto KIA (el nombre del archivo solo se muestra como
 * sugerencia), resolver cada ventana (omitir, fusionar, conservar o reemplazar la registrada, dejar abierta),
 * acordar un total distinto del archivo, y aplicar, omitir con motivo o dejar la fila pendiente (FR-027 a FR-029).
 */
@Component({
  selector: 'sarc-row-decision-panel',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    PointPicker,
  ],
  template: `
    <h2 mat-dialog-title>Resolver la fila {{ row().rowNumber }}</h2>
    <mat-dialog-content>
      <p class="target">
        <strong>{{ classificationLabels[row().classification] }}</strong>
        @for (issue of row().interpretation.rowIssues; track issue.code) {
          <span class="issue">{{ issueText(issue) }}</span>
        }
      </p>

      @if (needsLink()) {
        <section class="block" aria-label="Vincular a un punto">
          <h3>Punto de carga</h3>
          @if (row().interpretation.name || row().interpretation.code) {
            <p class="muted">
              El archivo trae «{{ row().interpretation.name || '—' }}» con el código «{{
                row().interpretation.code || '—'
              }}». El nombre es solo una sugerencia: elija el punto.
            </p>
          }
          <sarc-point-picker [point]="linkTarget()" (pointChange)="linkTarget.set($event)" />
          <button
            matButton="tonal"
            type="button"
            [disabled]="busy() || !linkTarget()"
            (click)="link()"
          >
            Vincular a este punto
          </button>
        </section>
      }

      @if (windows().length > 0) {
        <section class="block" aria-label="Ventanas de la fila">
          <h3>Ventanas</h3>
          <ul class="windows">
            @for (window of windows(); track window.pair) {
              <li class="window">
                <p>
                  <strong>Par {{ window.pair }}</strong> · {{ windowText(window) }}
                  @if (window.minutes) {
                    · {{ durationText(window.minutes) }}
                  }
                </p>
                @for (issue of window.issues; track issue.code) {
                  <p class="issue">{{ issueText(issue) }}</p>
                }
                <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Qué hacer con la ventana {{ window.pair }}</mat-label>
                  <mat-select
                    [value]="choice(window.pair).resolution"
                    (selectionChange)="setResolution(window.pair, $event.value)"
                  >
                    <mat-option value="">Sin decisión</mat-option>
                    @for (option of optionsOf(window); track option.value) {
                      <mat-option [value]="option.value" [disabled]="!!option.disabledReason">
                        {{ option.label }}
                        @if (option.disabledReason) {
                          <span class="muted"> — {{ option.disabledReason }}</span>
                        }
                      </mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                @if (choice(window.pair).resolution === 'REPLACE') {
                  <mat-form-field
                    floatLabel="always"
                    appearance="outline"
                    subscriptSizing="dynamic"
                  >
                    <mat-label>Justificación del reemplazo</mat-label>
                    <textarea
                      matInput
                      rows="2"
                      maxlength="500"
                      [value]="choice(window.pair).justification"
                      (input)="setJustification(window.pair, valueOf($event))"
                    ></textarea>
                  </mat-form-field>
                }
              </li>
            }
          </ul>
        </section>
      }

      @if (hasTotalMismatch()) {
        <section class="block" aria-label="Total del archivo">
          <h3>Total del archivo</h3>
          <p>
            El archivo dice {{ minutesText(row().interpretation.fileTotalMinutes) }}; las ventanas
            suman {{ minutesText(row().interpretation.computedTotalMinutes) }}.
            @if (present(row().interpretation.computedWithDuplicatesMinutes)) {
              Con la ventana duplicada serían
              {{ minutesText(row().interpretation.computedWithDuplicatesMinutes) }}.
            }
          </p>
          <mat-checkbox [checked]="acknowledge()" (change)="acknowledge.set($event.checked)">
            Acepto la diferencia: el total del archivo no es el correcto
          </mat-checkbox>
        </section>
      }

      <section class="block" aria-label="Destino de la fila">
        <h3>Qué se hace con la fila</h3>
        <mat-radio-group
          [value]="decision()"
          (change)="decision.set($event.value)"
          aria-label="Destino de la fila"
        >
          <mat-radio-button value="APPLY">{{
            resolving ? 'Incorporar ahora' : 'Incorporar al confirmar'
          }}</mat-radio-button>
          <mat-radio-button value="SKIP">Omitir la fila</mat-radio-button>
          @if (canKeepPending()) {
            <mat-radio-button value="KEEP_PENDING">Dejarla pendiente de resolver</mat-radio-button>
          }
        </mat-radio-group>
        @if (decision() === 'SKIP') {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Motivo de la omisión</mat-label>
            <input
              matInput
              maxlength="500"
              [value]="skipReason()"
              (input)="skipReason.set(valueOf($event))"
            />
          </mat-form-field>
        }
      </section>

      @if (error()) {
        <div class="form-alert" role="alert">
          <p>{{ error() }}</p>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
        Cancelar
      </button>
      <button matButton="filled" type="button" [disabled]="busy() || !canSave()" (click)="save()">
        Guardar decisión
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    h3 {
      margin: 0 0 var(--sarc-space-2);
      font: var(--mat-sys-title-small);
    }

    .block {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      margin-bottom: var(--sarc-space-4);
      min-width: min(520px, 75vw);
    }

    .target {
      display: flex;
      flex-wrap: wrap;
      gap: var(--sarc-space-2) var(--sarc-space-3);
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

      p {
        margin: 0;
      }
    }

    .issue {
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class RowDecisionPanel {
  private readonly data = inject<ImportRowDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RowDecisionPanel, IntermittencyImportRow>);

  /** La fila se renueva despues de vincularla: su version cambia. */
  protected readonly row = signal(this.data.row);
  protected readonly classificationLabels = CLASSIFICATION_LABELS;
  protected readonly durationText = durationText;
  protected readonly present = present;
  protected readonly issueText = issueText;
  protected readonly linkTarget = signal<KiaPointResponse | null>(null);
  protected readonly resolving = this.data.resolving === true;
  // Una fila pendiente llega con KEEP_PENDING, que al resolverla el servidor rechaza: se parte de incorporar.
  protected readonly decision = signal<RowDecision>(
    this.resolving && this.data.row.decision === 'KEEP_PENDING'
      ? 'APPLY'
      : (this.data.row.decision ?? 'APPLY'),
  );
  protected readonly skipReason = signal(this.data.row.skipReason ?? '');
  protected readonly acknowledge = signal(this.data.row.interpretation.totalAcknowledged);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  private readonly choices = signal<Readonly<Record<number, WindowChoice>>>(
    Object.fromEntries(
      this.data.row.interpretation.windows.map((window) => [
        window.pair,
        { resolution: window.decision ?? '', justification: '' } satisfies WindowChoice,
      ]),
    ),
  );
  private readonly initial = JSON.stringify(this.choices()) + this.decision() + this.acknowledge();

  /** Las ventanas que cuentan: las fusionadas en otra ya no se deciden. */
  protected readonly windows = computed(() =>
    this.row().interpretation.windows.filter((window) => window.mergedIntoPair == null),
  );
  // En una fila de un lote confirmado el vinculo queda anotado en la fila hasta resolverla.
  protected readonly needsLink = computed(
    () =>
      !this.row().equipmentId &&
      (this.row().classification === 'PENDING_LINK' ||
        this.row().interpretation.rowIssues.some((issue) => issue.code === 'MISSING_CODE')),
  );
  protected readonly hasTotalMismatch = computed(() =>
    this.row().interpretation.rowIssues.some((issue) => issue.code === 'TOTAL_MISMATCH'),
  );
  protected readonly canKeepPending = computed(
    () =>
      !this.resolving &&
      (this.row().classification === 'PENDING_LINK' || this.row().classification === 'CONFLICT'),
  );
  protected readonly canSave = computed(
    () =>
      (this.decision() !== 'SKIP' || this.skipReason().trim() !== '') &&
      this.windows().every((window) => {
        const choice = this.choice(window.pair);
        return choice.resolution !== 'REPLACE' || choice.justification.trim() !== '';
      }),
  );
  protected readonly unsaved = guardUnsavedChanges(
    () => JSON.stringify(this.choices()) + this.decision() + this.acknowledge() !== this.initial,
  );

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected choice(pair: number): WindowChoice {
    return this.choices()[pair] ?? { resolution: '', justification: '' };
  }

  protected optionsOf(window: RowWindow): ResolutionOption[] {
    return resolutionOptions(window, this.row());
  }

  protected setResolution(pair: number, resolution: WindowResolution | ''): void {
    this.choices.update((choices) => ({
      ...choices,
      [pair]: { ...this.choice(pair), resolution },
    }));
  }

  protected setJustification(pair: number, justification: string): void {
    this.choices.update((choices) => ({
      ...choices,
      [pair]: { ...this.choice(pair), justification },
    }));
  }

  protected windowText(window: RowWindow): string {
    if (window.startAt) {
      return `${formatInstant(window.startAt)} → ${window.endAt ? formatInstant(window.endAt) : 'abierta'}`;
    }
    return `${sourceCellText(window.startText)} → ${sourceCellText(window.endText)}`;
  }

  protected minutesText(minutes: number | null | undefined): string {
    return minutes === null || minutes === undefined
      ? '—'
      : `${durationText(minutes)} (${minutes} min)`;
  }

  protected async link(): Promise<void> {
    const point = this.linkTarget();
    if (!point || this.busy()) return;
    await this.run(async () => {
      const changed = await this.data.actions.linkRow(this.row(), point.id);
      this.row.set(changed.row);
      this.linkTarget.set(null);
    });
  }

  protected async save(): Promise<void> {
    if (!this.canSave() || this.busy()) return;
    const windows = this.windows()
      .filter((window) => this.choice(window.pair).resolution !== (window.decision ?? ''))
      .map((window) => {
        const choice = this.choice(window.pair);
        return {
          pair: window.pair,
          ...(choice.resolution === '' ? {} : { resolution: choice.resolution }),
          ...(choice.resolution === 'REPLACE'
            ? { justification: choice.justification.trim() }
            : {}),
        };
      });
    // Una decision de REPLACE que no cambio de resolucion pero cambio de justificacion tambien se envia.
    const body: Omit<ImportRowDecisionRequest, 'version'> = {
      decision: this.decision(),
      ...(this.decision() === 'SKIP' ? { skipReason: this.skipReason().trim() } : {}),
      acknowledgeTotal: this.acknowledge() && this.hasTotalMismatch(),
      windows,
    };
    await this.run(async () => {
      const changed = await this.data.actions.decideRow(this.row(), body);
      this.ref.close(changed.row);
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await action();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'No se pudo guardar la decisión.');
    } finally {
      this.busy.set(false);
    }
  }
}
