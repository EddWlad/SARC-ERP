import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';

export interface AnnulDialogData {
  readonly title: string;
  readonly message: string;
  readonly save: (reason: string) => Promise<void>;
}

/**
 * Anula un evento o una evidencia con un motivo (FR-036, FR-037). Nada se borra: lo anulado sigue visible en la
 * cronología, tachado y con su motivo, y en la auditoría.
 */
@Component({
  selector: 'sarc-annul-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">{{ data.message }}</p>
        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Motivo</mat-label>
          <input matInput autocomplete="off" [formField]="annulForm.reason" />
          @for (error of annulForm.reason().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Anulando…' : 'Anular' }}
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
export class AnnulDialog {
  protected readonly data = inject<AnnulDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<AnnulDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.annulForm().dirty());

  protected readonly model = signal({ reason: '' });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly annulForm = form(this.model, (path) => {
    required(path.reason, { message: 'Indique el motivo de la anulación' });
    maxLength(path.reason, 300, { message: 'Máximo 300 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.annulForm, async () => {
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save(this.model().reason.trim());
        this.dialogRef.close(true);
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
