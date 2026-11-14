import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { MeasurementFacade } from '../../application/measurement-facade';
import { PERIOD_TYPE_LABELS, PeriodType } from '../../domain/uptime-labels';

const text = (value: number | null | undefined) => (value == null ? '' : String(value));

/** Corrige los valores en una revision nueva; la vigente sigue hasta validar. */
@Component({
  selector: 'sarc-correction-form',
  imports: [
    FormField,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Corregir medición</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        <div class="fields">
          <mat-form-field
            floatLabel="always"
            appearance="outline"
            class="wide"
            subscriptSizing="dynamic"
          >
            <mat-label>Tipo de período</mat-label>
            <mat-select [formField]="correction.periodType">
              @for (type of types; track type) {
                <mat-option [value]="type">{{ labels[type] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          @if (model().periodType === 'EXACT_WEEK') {
            <mat-checkbox class="wide" [formField]="correction.exactConfirmed">
              Confirmo que el período es la semana exacta
            </mat-checkbox>
          }
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Disponible (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="correction.available" />
          </mat-form-field>
          @if (!reported) {
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Ocupado (min)</mat-label>
              <input matInput inputmode="decimal" [formField]="correction.occupied" />
            </mat-form-field>
          }
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Con falla (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="correction.fault" />
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Fuera de línea (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="correction.offline" />
          </mat-form-field>
          <mat-form-field
            floatLabel="always"
            appearance="outline"
            class="wide"
            subscriptSizing="dynamic"
          >
            <mat-label>Justificación</mat-label>
            <textarea matInput rows="2" [formField]="correction.justification"></textarea>
          </mat-form-field>
        </div>
        @if (nonNumeric()) {
          <div class="form-alert" role="alert"><p>Ingrese solo números en los tiempos.</p></div>
        }
        @if (facade.error(); as error) {
          <div class="form-alert" role="alert">
            <p>{{ error }}</p>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="facade.busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="facade.busy()">
          Guardar corrección
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3) var(--sarc-space-4);
      min-width: min(480px, 75vw);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .wide {
      grid-column: 1 / -1;
    }

    .form-alert {
      margin-top: var(--sarc-space-2);
    }
  `,
})
export class CorrectionForm {
  /** Llega por datos: el dialogo vive fuera del arbol que provee la fachada de la pagina. */
  readonly facade = inject<MeasurementFacade>(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialogRef);
  protected readonly labels = PERIOD_TYPE_LABELS;
  protected readonly types = Object.keys(PERIOD_TYPE_LABELS) as PeriodType[];
  private readonly source = this.facade.detail();
  /** El informe semanal no trae Ocupado: se corrigen los otros tres y el uptime informado se conserva. */
  protected readonly reported = this.source?.uptimeBasis === 'REPORTED_PERCENT';
  readonly model = signal({
    periodType: (this.source?.periodType ?? 'ROLLING_24H') as PeriodType,
    exactConfirmed: false,
    available: text(this.source?.availableMinutes),
    occupied: text(this.source?.occupiedMinutes),
    fault: text(this.source?.faultMinutes),
    offline: text(this.source?.offlineMinutes),
    justification: '',
  });
  readonly correction = form(this.model, (path) => {
    required(path.available, { message: 'Requerido' });
    required(path.occupied, { message: 'Requerido', when: () => !this.reported });
    required(path.fault, { message: 'Requerido' });
    required(path.offline, { message: 'Requerido' });
    required(path.justification, { message: 'Requerida' });
    maxLength(path.justification, 500, { message: 'Máximo 500 caracteres' });
  });
  protected readonly unsaved = guardUnsavedChanges(() => this.correction().dirty());
  protected readonly nonNumeric = computed(() => {
    const { available, occupied, fault, offline } = this.model();
    return [available, this.reported ? '0' : occupied, fault, offline].some((value) =>
      Number.isNaN(Number(value)),
    );
  });

  onSubmit(event: Event): void {
    event.preventDefault();
    if (this.nonNumeric()) return;
    void submit(this.correction, async () => {
      const value = this.model();
      const saved = await this.facade.correct({
        periodType: value.periodType,
        exactConfirmed: value.exactConfirmed,
        availableMinutes: Number(value.available),
        occupiedMinutes: this.reported ? undefined : Number(value.occupied),
        faultMinutes: Number(value.fault),
        offlineMinutes: Number(value.offline),
        justification: value.justification.trim(),
      });
      if (saved) {
        this.correction().reset();
        this.dialog.close();
      }
    });
  }
}
