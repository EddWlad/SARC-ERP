import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { PriorityRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { PRIORITY_LABELS, Priority } from '../../domain/incident-labels';

export type PriorityResult = Omit<PriorityRequest, 'version'>;

export interface PriorityDialogData {
  readonly current: Priority;
  readonly save: (result: PriorityResult) => Promise<void>;
}

interface PriorityFormValue {
  priority: Priority | '';
  reason: string;
}

/**
 * Cambia la prioridad del ticket con un motivo (P-05): el sistema propone la prioridad según el protocolo y el analista
 * puede ajustarla. La prioridad vigente no se ofrece, porque no sería un cambio. Pasar a Crítica queda registrado como
 * evento para la campana futura.
 */
@Component({
  selector: 'sarc-priority-dialog',
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
    <h2 mat-dialog-title>Cambiar la prioridad</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">Prioridad actual: {{ labels[data.current] }}.</p>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Nueva prioridad</mat-label>
          <mat-select [formField]="priorityForm.priority">
            @for (option of options; track option) {
              <mat-option [value]="option">{{ labels[option] }}</mat-option>
            }
          </mat-select>
          @for (error of priorityForm.priority().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Motivo del cambio</mat-label>
          <textarea matInput rows="3" [formField]="priorityForm.reason"></textarea>
          @for (error of priorityForm.reason().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando…' : 'Cambiar prioridad' }}
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
export class PriorityDialog {
  protected readonly data = inject<PriorityDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<PriorityDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.priorityForm().dirty());

  protected readonly labels = PRIORITY_LABELS;
  protected readonly options = (Object.keys(PRIORITY_LABELS) as Priority[]).filter(
    (priority) => priority !== this.data.current,
  );

  protected readonly model = signal<PriorityFormValue>({ priority: '', reason: '' });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly priorityForm = form(this.model, (path) => {
    required(path.priority, { message: 'Seleccione la nueva prioridad' });
    required(path.reason, { message: 'Indique el motivo del cambio' });
    maxLength(path.reason, 300, { message: 'Máximo 300 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.priorityForm, async () => {
      const value = this.model();
      if (value.priority === '') return;
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({ priority: value.priority, reason: value.reason.trim() });
        this.dialogRef.close(true);
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
