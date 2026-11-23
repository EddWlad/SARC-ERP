import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { ReclassifyRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  ElementClass,
  INCIDENT_TYPE_LABELS,
  INCIDENT_TYPES_BY_CLASS,
  IncidentType,
} from '../../domain/incident-labels';

export type ReclassifyResult = Omit<ReclassifyRequest, 'version'>;

export interface ReclassifyDialogData {
  readonly elementClass: ElementClass;
  readonly current: IncidentType;
  readonly save: (result: ReclassifyResult) => Promise<void>;
}

interface ReclassifyFormValue {
  incidentType: IncidentType | '';
  reason: string;
}

/**
 * Cambia el tipo de incidencia con un motivo (FR-021). Solo ofrece los tipos que admite la clase del elemento y no el
 * vigente. Reclasificar recalcula la prioridad propuesta, pero no cambia la prioridad vigente: eso se hace aparte.
 */
@Component({
  selector: 'sarc-reclassify-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>Reclasificar la incidencia</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">
          Tipo actual: {{ labels[data.current] }}. La prioridad vigente no cambia; solo se recalcula
          la propuesta.
        </p>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Nuevo tipo de incidencia</mat-label>
          <mat-select [formField]="reclassifyForm.incidentType">
            @for (option of options; track option) {
              <mat-option [value]="option">{{ labels[option] }}</mat-option>
            }
          </mat-select>
          @for (error of reclassifyForm.incidentType().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Motivo de la reclasificación</mat-label>
          <textarea matInput rows="3" [formField]="reclassifyForm.reason"></textarea>
          @for (error of reclassifyForm.reason().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando…' : 'Reclasificar' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      min-width: min(480px, 80vw);
    }
  `,
})
export class ReclassifyDialog {
  protected readonly data = inject<ReclassifyDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ReclassifyDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.reclassifyForm().dirty());

  protected readonly labels = INCIDENT_TYPE_LABELS;
  protected readonly options = INCIDENT_TYPES_BY_CLASS[this.data.elementClass].filter(
    (type) => type !== this.data.current,
  );

  protected readonly model = signal<ReclassifyFormValue>({ incidentType: '', reason: '' });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly reclassifyForm = form(this.model, (path) => {
    required(path.incidentType, { message: 'Seleccione el nuevo tipo' });
    required(path.reason, { message: 'Indique el motivo de la reclasificación' });
    maxLength(path.reason, 300, { message: 'Máximo 300 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.reclassifyForm, async () => {
      const value = this.model();
      if (value.incidentType === '') return;
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({ incidentType: value.incidentType, reason: value.reason.trim() });
        this.dialogRef.close(true);
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
