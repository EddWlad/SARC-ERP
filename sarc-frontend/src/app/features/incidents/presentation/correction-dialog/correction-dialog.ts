import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { CorrectDataRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { REPORT_ORIGIN_LABELS, ReportOrigin } from '../../domain/incident-labels';

export type CorrectionResult = Omit<CorrectDataRequest, 'version'>;

export interface CorrectionDialogData {
  readonly description: string;
  readonly reportOrigin: ReportOrigin;
  readonly reportOriginDetail: string | null | undefined;
  readonly save: (result: CorrectionResult) => Promise<void>;
}

interface CorrectionFormValue {
  description: string;
  reportOrigin: ReportOrigin;
  reportOriginDetail: string;
  justification: string;
}

/**
 * Corrige la descripción o el origen del aviso (FR-036). Toda corrección exige una justificación y deja un evento por
 * campo cambiado, con el valor anterior y el nuevo. El elemento afectado no se corrige aquí: un ticket abierto sobre
 * el elemento equivocado se cierra como «Registrado por error» y se crea otro.
 */
@Component({
  selector: 'sarc-correction-dialog',
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
    <h2 mat-dialog-title>Corregir datos</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">
          El elemento afectado no se corrige aquí. Si es el equivocado, cierre el ticket como
          «Registrado por error» y cree otro.
        </p>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Descripción</mat-label>
          <textarea matInput rows="3" [formField]="correctionForm.description"></textarea>
          @for (error of correctionForm.description().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Origen del aviso</mat-label>
          <mat-select [formField]="correctionForm.reportOrigin">
            @for (origin of origins; track origin) {
              <mat-option [value]="origin">{{ originLabels[origin] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (otherOrigin()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Detalle del origen</mat-label>
            <input matInput autocomplete="off" [formField]="correctionForm.reportOriginDetail" />
            @for (error of correctionForm.reportOriginDetail().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Justificación de la corrección</mat-label>
          <input matInput autocomplete="off" [formField]="correctionForm.justification" />
          <mat-hint>Queda en la cronología con el valor anterior.</mat-hint>
          @for (error of correctionForm.justification().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando…' : 'Corregir' }}
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
export class CorrectionDialog {
  private readonly data = inject<CorrectionDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CorrectionDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.correctionForm().dirty());

  protected readonly origins = Object.keys(REPORT_ORIGIN_LABELS) as ReportOrigin[];
  protected readonly originLabels = REPORT_ORIGIN_LABELS;

  protected readonly model = signal<CorrectionFormValue>({
    description: this.data.description,
    reportOrigin: this.data.reportOrigin,
    reportOriginDetail: this.data.reportOriginDetail ?? '',
    justification: '',
  });
  protected readonly otherOrigin = computed(() => this.model().reportOrigin === 'OTHER');

  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly correctionForm = form(this.model, (path) => {
    required(path.description, { message: 'La descripción no puede quedar vacía' });
    maxLength(path.description, 500, { message: 'Máximo 500 caracteres' });
    required(path.reportOriginDetail, {
      when: () => this.otherOrigin(),
      message: 'Indique el detalle del origen',
    });
    maxLength(path.reportOriginDetail, 200, { message: 'Máximo 200 caracteres' });
    required(path.justification, { message: 'Una corrección exige una justificación' });
    maxLength(path.justification, 500, { message: 'Máximo 500 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.correctionForm, async () => {
      const value = this.model();
      const descriptionChanged = value.description.trim() !== this.data.description;
      const originChanged =
        value.reportOrigin !== this.data.reportOrigin ||
        (value.reportOrigin === 'OTHER' &&
          value.reportOriginDetail.trim() !== (this.data.reportOriginDetail ?? ''));
      if (!descriptionChanged && !originChanged) {
        this.errorMessage.set('No hay ningún cambio que corregir.');
        return;
      }
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({
          justification: value.justification.trim(),
          ...(descriptionChanged ? { description: value.description.trim() } : {}),
          ...(originChanged
            ? {
                reportOrigin: value.reportOrigin,
                reportOriginDetail:
                  value.reportOrigin === 'OTHER' ? value.reportOriginDetail.trim() : null,
              }
            : {}),
        });
        this.dialogRef.close(true);
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
