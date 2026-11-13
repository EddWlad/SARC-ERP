import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucidePlus, LucideTrash2 } from '@lucide/angular';

import { EditImportRowRequest } from '../../../../core/api/generated';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { ImportRowDialogData } from '../../application/import-row-actions';
import { IntermittencyImportRow, RowWindow } from '../../domain/intermittency-import';
import {
  durationText,
  localParts,
  minutesBetween,
  operatingInstant,
} from '../../domain/intermittency-time';

/** Una ventana de la fila en edicion, con sus valores originales para saber que cambio. */
interface WindowForm {
  readonly pair: number;
  readonly original: RowWindow;
  readonly start: string;
  readonly end: string;
  readonly endsNextDay: boolean;
  readonly test: boolean;
  readonly removed: boolean;
}

interface NewWindowForm {
  readonly key: number;
  readonly start: string;
  readonly end: string;
}

/** `aaaa-mm-ddThh:mm` de un instante, o vacio. */
function localInput(instant: string | null | undefined): string {
  if (!instant) return '';
  const { date, time } = localParts(instant);
  return `${date}T${time}`;
}

/** Un `datetime-local` como instante con el desfase de la zona operativa. */
function toInstant(local: string): string {
  const [date, time] = local.split('T');
  return operatingInstant(date, time);
}

/**
 * Corrige una fila en la vista previa: fecha, codigo, comentario, marca de prueba, el inicio y el fin de cada
 * ventana (con fecha y hora completas, tambien para una caida de varios dias), la marca de dia siguiente, y agrega
 * u omite pares. El original no se toca: el servidor guarda cada cambio con su valor anterior (FR-026).
 */
@Component({
  selector: 'sarc-import-row-editor',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    LucidePlus,
    LucideTrash2,
  ],
  template: `
    <h2 mat-dialog-title>Corregir la fila {{ row.rowNumber }}</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        <p class="note">
          Original: {{ originalSummary }}. Lo que cambie queda registrado con su valor anterior.
        </p>
        <div class="fields">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Fecha de la fila</mat-label>
            <input matInput type="date" [value]="date()" (input)="date.set(valueOf($event))" />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Código del cargador</mat-label>
            <input matInput [value]="code()" (input)="code.set(valueOf($event))" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="wide" subscriptSizing="dynamic">
            <mat-label>Comentario</mat-label>
            <input
              matInput
              maxlength="500"
              [value]="comment()"
              (input)="comment.set(valueOf($event))"
            />
          </mat-form-field>
        </div>
        <mat-checkbox [checked]="rowTest()" (change)="rowTest.set($event.checked)">
          Marcar toda la fila como prueba
        </mat-checkbox>

        <h3>Ventanas</h3>
        <ul class="windows">
          @for (window of windows(); track window.pair) {
            <li class="window" [class.removed]="window.removed">
              <p class="muted">
                Par {{ window.pair }}
                @if (window.original.startText || window.original.endText) {
                  · Original: {{ window.original.startText || '—' }} →
                  {{ window.original.endText || '—' }}
                }
                @if (window.original.added) {
                  · Agregado en esta revisión
                }
              </p>
              @for (issue of window.original.issues; track issue.code) {
                <p class="issue">{{ issue.message }}</p>
              }
              @if (!window.removed) {
                <div class="fields">
                  <mat-form-field appearance="outline" subscriptSizing="dynamic">
                    <mat-label>Inicio {{ window.pair }}</mat-label>
                    <input
                      matInput
                      type="datetime-local"
                      [value]="window.start"
                      (input)="patchWindow(window.pair, { start: valueOf($event) })"
                    />
                  </mat-form-field>
                  <mat-form-field appearance="outline" subscriptSizing="dynamic">
                    <mat-label>Fin {{ window.pair }}</mat-label>
                    <input
                      matInput
                      type="datetime-local"
                      [value]="window.end"
                      (input)="patchWindow(window.pair, { end: valueOf($event) })"
                    />
                  </mat-form-field>
                </div>
                <div class="options">
                  <mat-checkbox
                    [checked]="window.endsNextDay"
                    [disabled]="!!window.end && window.end !== localInputOf(window.original.endAt)"
                    (change)="patchWindow(window.pair, { endsNextDay: $event.checked })"
                  >
                    Termina el día siguiente
                  </mat-checkbox>
                  <mat-checkbox
                    [checked]="window.test"
                    (change)="patchWindow(window.pair, { test: $event.checked })"
                  >
                    Prueba
                  </mat-checkbox>
                  @if (minutesOf(window); as minutes) {
                    <span class="badge-info">{{ minutes }}</span>
                  }
                </div>
              } @else {
                <p class="note">Esta ventana se omite de la fila.</p>
              }
              <button
                matButton
                type="button"
                (click)="patchWindow(window.pair, { removed: !window.removed })"
              >
                <svg lucideTrash2 [size]="18" aria-hidden="true"></svg>
                {{
                  window.removed
                    ? 'Restaurar el par ' + window.pair
                    : 'Omitir el par ' + window.pair
                }}
              </button>
            </li>
          }
          @for (added of newWindows(); track added.key) {
            <li class="window">
              <p class="muted">Ventana nueva</p>
              <div class="fields">
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Inicio</mat-label>
                  <input
                    matInput
                    type="datetime-local"
                    [value]="added.start"
                    (input)="patchNew(added.key, { start: valueOf($event) })"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Fin</mat-label>
                  <input
                    matInput
                    type="datetime-local"
                    [value]="added.end"
                    (input)="patchNew(added.key, { end: valueOf($event) })"
                  />
                </mat-form-field>
              </div>
              <button matButton type="button" (click)="removeNew(added.key)">
                <svg lucideTrash2 [size]="18" aria-hidden="true"></svg>
                Quitar la ventana nueva
              </button>
            </li>
          }
        </ul>
        <button matButton="tonal" type="button" (click)="addNew()">
          <svg lucidePlus [size]="18" aria-hidden="true"></svg>
          Agregar ventana
        </button>

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
        <button matButton="filled" type="submit" [disabled]="busy() || !hasChanges()">
          Guardar y volver a validar
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    h3 {
      margin: var(--sarc-space-4) 0 var(--sarc-space-2);
      font: var(--mat-sys-title-small);
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-3);
      min-width: min(560px, 75vw);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .wide {
      grid-column: 1 / -1;
    }

    .windows {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin: 0 0 var(--sarc-space-3);
      padding: 0;
      list-style: none;
    }

    .window {
      padding: var(--sarc-space-3);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-sm);

      &.removed {
        opacity: 0.7;
      }
    }

    .options {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-3);
    }

    .muted,
    .note {
      margin: 0 0 var(--sarc-space-2);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .issue {
      margin: 0 0 var(--sarc-space-2);
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }

    .badge-info {
      padding: 2px var(--sarc-space-2);
      border-radius: 999px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-small);
    }
  `,
})
export class ImportRowEditor {
  private readonly data = inject<ImportRowDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ImportRowEditor, IntermittencyImportRow>);

  protected readonly row = this.data.row;
  protected readonly localInputOf = localInput;
  private readonly interpretation = this.row.interpretation;

  protected readonly date = signal(this.interpretation.date ?? '');
  protected readonly code = signal(this.interpretation.code);
  protected readonly comment = signal(this.interpretation.comment);
  protected readonly rowTest = signal(
    this.interpretation.windows.length > 0 &&
      this.interpretation.windows.every((window) => window.test),
  );
  private readonly rowTestInitial = this.rowTest();
  protected readonly windows = signal<WindowForm[]>(
    this.interpretation.windows
      .filter((window) => window.mergedIntoPair == null)
      .map((window) => ({
        pair: window.pair,
        original: window,
        start: localInput(window.startAt),
        end: localInput(window.endAt),
        endsNextDay: window.endsNextDay,
        test: window.test,
        removed: false,
      })),
  );
  protected readonly newWindows = signal<NewWindowForm[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  private nextKey = 1;

  protected readonly originalSummary = this.row.raw
    .filter((cell) => cell.value !== '' && ['date', 'code'].includes(cell.key))
    .map((cell) => `${cell.label} ${cell.value}`)
    .join(' · ');

  /** Lo que se enviaria: nada si no hay cambios. */
  private readonly body = computed<Omit<EditImportRowRequest, 'version'>>(() => this.buildBody());
  protected readonly hasChanges = computed(() => {
    const body = this.body();
    return (
      (body.edits?.length ?? 0) > 0 ||
      (body.addWindows?.length ?? 0) > 0 ||
      (body.removePairs?.length ?? 0) > 0
    );
  });
  protected readonly unsaved = guardUnsavedChanges(() => this.hasChanges());

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected patchWindow(pair: number, patch: Partial<Omit<WindowForm, 'pair' | 'original'>>): void {
    this.windows.update((windows) =>
      windows.map((window) => (window.pair === pair ? { ...window, ...patch } : window)),
    );
  }

  protected addNew(): void {
    this.newWindows.update((windows) => [...windows, { key: this.nextKey++, start: '', end: '' }]);
  }

  protected patchNew(key: number, patch: Partial<Omit<NewWindowForm, 'key'>>): void {
    this.newWindows.update((windows) =>
      windows.map((window) => (window.key === key ? { ...window, ...patch } : window)),
    );
  }

  protected removeNew(key: number): void {
    this.newWindows.update((windows) => windows.filter((window) => window.key !== key));
  }

  /** Duracion de la ventana con lo digitado, si se puede calcular. */
  protected minutesOf(window: WindowForm): string {
    if (!window.start || !window.end) return '';
    const [startDate, startTime] = window.start.split('T');
    const [endDate, endTime] = window.end.split('T');
    const minutes = minutesBetween(startDate, startTime, endDate, endTime);
    return minutes !== null && minutes > 0 ? durationText(minutes) : '';
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.hasChanges() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const changed = await this.data.actions.editRow(this.row, this.body());
      this.ref.close(changed.row);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'No se pudo guardar la corrección.');
    } finally {
      this.busy.set(false);
    }
  }

  private buildBody(): Omit<EditImportRowRequest, 'version'> {
    const edits: NonNullable<EditImportRowRequest['edits']> = [];
    const interpretation = this.interpretation;
    if (this.date() !== (interpretation.date ?? '')) {
      edits.push({ target: 'date', value: this.date() });
    }
    if (this.code() !== interpretation.code) {
      edits.push({ target: 'code', value: this.code() });
    }
    if (this.comment() !== interpretation.comment) {
      edits.push({ target: 'comment', value: this.comment() });
    }
    if (this.rowTest() !== this.rowTestInitial) {
      edits.push({ target: 'test', value: String(this.rowTest()) });
    }
    const removePairs: number[] = [];
    for (const window of this.windows()) {
      if (window.removed) {
        removePairs.push(window.pair);
        continue;
      }
      const original = window.original;
      const startChanged = window.start !== '' && window.start !== localInput(original.startAt);
      const endChanged = window.end !== '' && window.end !== localInput(original.endAt);
      if (startChanged || endChanged || window.endsNextDay !== original.endsNextDay) {
        edits.push({
          target: 'window',
          pair: window.pair,
          ...(startChanged ? { startAt: toInstant(window.start) } : {}),
          ...(endChanged ? { endAt: toInstant(window.end) } : {}),
          ...(!endChanged && window.endsNextDay !== original.endsNextDay
            ? { endsNextDay: window.endsNextDay }
            : {}),
        });
      }
      if (window.test !== original.test && !this.rowTestChanged(window)) {
        edits.push({ target: 'test', pair: window.pair, value: String(window.test) });
      }
    }
    const addWindows = this.newWindows()
      .filter((window) => window.start !== '')
      .map((window) => ({
        startAt: toInstant(window.start),
        ...(window.end ? { endAt: toInstant(window.end) } : {}),
      }));
    return { edits, addWindows, removePairs };
  }

  /** La marca de toda la fila ya cubre a la ventana; no se repite. */
  private rowTestChanged(window: WindowForm): boolean {
    return this.rowTest() !== this.rowTestInitial && window.test === this.rowTest();
  }
}
