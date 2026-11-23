import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideTriangleAlert } from '@lucide/angular';

import { ReopenRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { isRetroactiveLocal, operatingInstant } from '../../domain/incident-time';

export type ReopenResult = Omit<ReopenRequest, 'version'>;

export interface ReopenDialogData {
  /** Código del ticket, para nombrarlo: el ticket conserva su número al reabrirse. */
  readonly code: string;
  readonly save: (result: ReopenResult) => Promise<void>;
}

interface ReopenFormValue {
  reason: string;
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacío es el momento actual. */
  t0: string;
  t0Reason: string;
}

/**
 * Reabre un ticket cerrado cuando el caso continúa (FR-049). Pide el motivo y la hora de la nueva detección: crea un
 * episodio nuevo con su propio T0 y conserva el número y los episodios anteriores. Una hora anterior al momento de
 * guardar exige su propio motivo. Un ticket cerrado como duplicado o por error no llega aquí: no se reabre.
 */
@Component({
  selector: 'sarc-reopen-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>Reabrir {{ data.code }}</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">
          El ticket conserva su número y sus episodios anteriores. Se abre un episodio nuevo con su
          propio T0.
        </p>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Motivo de la reapertura</mat-label>
          <textarea matInput rows="3" [formField]="reopenForm.reason"></textarea>
          @for (error of reopenForm.reason().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Hora de la nueva detección (T0)</mat-label>
          <input matInput type="datetime-local" [formField]="reopenForm.t0" />
          <mat-hint>Vacía, es el momento actual. Hora de Ecuador.</mat-hint>
        </mat-form-field>

        @if (retroactive()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Motivo de la hora anterior</mat-label>
            <input matInput autocomplete="off" [formField]="reopenForm.t0Reason" />
            @for (error of reopenForm.t0Reason().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Reabriendo…' : 'Reabrir ticket' }}
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
export class ReopenDialog {
  protected readonly data = inject<ReopenDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ReopenDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.reopenForm().dirty());

  protected readonly model = signal<ReopenFormValue>({ reason: '', t0: '', t0Reason: '' });
  protected readonly retroactive = computed(() => isRetroactiveLocal(this.model().t0));
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly reopenForm = form(this.model, (path) => {
    required(path.reason, { message: 'Indique el motivo de la reapertura' });
    maxLength(path.reason, 500, { message: 'Máximo 500 caracteres' });
    required(path.t0Reason, {
      when: () => this.retroactive(),
      message: 'Una hora anterior exige un motivo',
    });
    maxLength(path.t0Reason, 300, { message: 'Máximo 300 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.reopenForm, async () => {
      const value = this.model();
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({
          reason: value.reason.trim(),
          t0: operatingInstant(value.t0),
          t0Reason: this.retroactive() ? value.t0Reason.trim() : null,
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
