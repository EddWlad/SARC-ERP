import { Component, inject, OnInit, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { EligibleResponsibleResponse, HandoverRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { operatingInstant } from '../../domain/incident-time';

/** `handover` es el relevo de turno con sus cuatro datos; `assign` solo cambia el responsable. */
export type HandoverMode = 'handover' | 'assign';

export type HandoverResult = Omit<HandoverRequest, 'version'>;

export interface HandoverDialogData {
  readonly mode: HandoverMode;
  /** Responsable actual: no se ofrece como entrante. */
  readonly currentResponsibleId: string;
  /** Editores activos elegibles (`GET /responsibles`). */
  readonly responsibles: () => Promise<EligibleResponsibleResponse[]>;
  readonly save: (result: HandoverResult) => Promise<void>;
}

interface HandoverFormValue {
  incomingResponsibleId: string;
  currentStatus: string;
  pending: string;
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacío si no hay próxima verificación. */
  nextCheckAt: string;
  closureCriterion: string;
}

/**
 * Relevo de turno (FR-033): el responsable saliente deja el estado actual, los pendientes, la próxima verificación y
 * el criterio previsto de cierre, y el entrante pasa a ser el responsable. El relevo no crea un ticket nuevo. El
 * entrante se elige de los editores activos; la misma lista sirve para solo asignar un responsable.
 */
@Component({
  selector: 'sarc-handover-dialog',
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
    <h2 mat-dialog-title>{{ assigning ? 'Asignar responsable' : 'Relevo de turno' }}</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ assigning ? 'Responsable' : 'Responsable entrante' }}</mat-label>
          <mat-select [formField]="handoverForm.incomingResponsibleId">
            @for (person of candidates(); track person.id) {
              <mat-option [value]="person.id">{{ person.name }}</mat-option>
            }
          </mat-select>
          @if (loadFailed()) {
            <mat-hint>No se pudo cargar la lista. Cierre y vuelva a intentar.</mat-hint>
          }
          @for (error of handoverForm.incomingResponsibleId().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        @if (!assigning) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Estado actual</mat-label>
            <textarea matInput rows="2" [formField]="handoverForm.currentStatus"></textarea>
            @for (error of handoverForm.currentStatus().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Pendientes</mat-label>
            <textarea matInput rows="2" [formField]="handoverForm.pending"></textarea>
            @for (error of handoverForm.pending().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Próxima verificación</mat-label>
            <input matInput type="datetime-local" [formField]="handoverForm.nextCheckAt" />
            <mat-hint>Opcional. Hora de Ecuador.</mat-hint>
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Criterio previsto de cierre</mat-label>
            <textarea matInput rows="2" [formField]="handoverForm.closureCriterion"></textarea>
            @for (error of handoverForm.closureCriterion().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy() || loadFailed()">
          {{ busy() ? 'Guardando…' : assigning ? 'Asignar' : 'Registrar relevo' }}
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
export class HandoverDialog implements OnInit {
  private readonly data = inject<HandoverDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<HandoverDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.handoverForm().dirty());

  protected readonly assigning = this.data.mode === 'assign';

  protected readonly model = signal<HandoverFormValue>({
    incomingResponsibleId: '',
    currentStatus: '',
    pending: '',
    nextCheckAt: '',
    closureCriterion: '',
  });
  protected readonly candidates = signal<readonly EligibleResponsibleResponse[]>([]);
  protected readonly loadFailed = signal(false);
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly handoverForm = form(this.model, (path) => {
    required(path.incomingResponsibleId, { message: 'Seleccione a la persona' });
    required(path.currentStatus, {
      when: () => !this.assigning,
      message: 'Indique el estado actual',
    });
    maxLength(path.currentStatus, 1000, { message: 'Máximo 1000 caracteres' });
    required(path.pending, { when: () => !this.assigning, message: 'Indique los pendientes' });
    maxLength(path.pending, 1000, { message: 'Máximo 1000 caracteres' });
    required(path.closureCriterion, {
      when: () => !this.assigning,
      message: 'Indique el criterio previsto de cierre',
    });
    maxLength(path.closureCriterion, 1000, { message: 'Máximo 1000 caracteres' });
  });

  ngOnInit(): void {
    this.data
      .responsibles()
      .then((people) =>
        this.candidates.set(
          people.filter((person) => person.id !== this.data.currentResponsibleId),
        ),
      )
      .catch(() => this.loadFailed.set(true));
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.handoverForm, async () => {
      const value = this.model();
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({
          incomingResponsibleId: value.incomingResponsibleId,
          currentStatus: value.currentStatus.trim(),
          pending: value.pending.trim(),
          nextCheckAt: operatingInstant(value.nextCheckAt),
          closureCriterion: value.closureCriterion.trim(),
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
