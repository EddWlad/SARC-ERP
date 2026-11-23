import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { MilestoneRequest, TicketMilestone } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  MILESTONE_KIND_LABELS,
  MILESTONE_STATUS_LABELS,
  MilestoneKind,
  MilestoneStatus,
} from '../../domain/incident-labels';
import { isRetroactiveLocal, localInput, operatingInstant } from '../../domain/incident-time';

/** Estados a los que se puede registrar un hito; «Pendiente» solo es el punto de partida (FR-041). */
type EditableStatus = Exclude<MilestoneStatus, 'PENDING'>;

export interface MilestoneDialogData {
  readonly kind: MilestoneKind;
  readonly current: TicketMilestone;
  /** El episodio está cerrado: cualquier cambio es una corrección y exige justificación (FR-036). */
  readonly closed?: boolean;
  readonly save: (body: Omit<MilestoneRequest, 'version'>) => Promise<void>;
}

interface MilestoneFormValue {
  status: EditableStatus;
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacío es el momento actual. */
  occurredAt: string;
  reason: string;
  justification: string;
}

/** Código del servidor cuando el orden T1 ≤ T2 ≤ T3 no se cumple y falta la confirmación (FR-043). */
const ORDER_UNCONFIRMED = 'MILESTONE_ORDER_UNCONFIRMED';

/**
 * Registra o corrige un hito (FR-040 a FR-043). La hora vacía es el momento actual; una hora anterior exige motivo y
 * queda marcada como retroactiva. Marcar «No aplica» o «Sin dato» exige motivo. Corregir un hito ya registrado exige
 * una justificación y deja la revisión anterior en el historial. Si el orden de los hitos no se cumple, el servidor lo
 * advierte y se confirma con una casilla; nunca se confirma sin avisar.
 */
@Component({
  selector: 'sarc-milestone-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>{{ correcting ? 'Corregir' : 'Registrar' }} {{ title }}</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }

        @if (statuses.length > 1) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Estado del hito</mat-label>
            <mat-select [formField]="milestoneForm.status">
              @for (status of statuses; track status) {
                <mat-option [value]="status">{{ statusLabels[status] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        @if (recorded()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Hora</mat-label>
            <input matInput type="datetime-local" [formField]="milestoneForm.occurredAt" />
            <mat-hint>Vacía, es el momento actual. Hora de Ecuador.</mat-hint>
          </mat-form-field>
        } @else {
          <p class="note">El hito queda sin hora y no entra en las duraciones.</p>
        }

        @if (reasonNeeded()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ recorded() ? 'Motivo de la hora anterior' : 'Motivo' }}</mat-label>
            <input matInput autocomplete="off" [formField]="milestoneForm.reason" />
            @for (error of milestoneForm.reason().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        @if (correcting) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Justificación de la corrección</mat-label>
            <input matInput autocomplete="off" [formField]="milestoneForm.justification" />
            <mat-hint>La revisión anterior se conserva en el historial.</mat-hint>
            @for (error of milestoneForm.justification().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        @if (orderWarning()) {
          <mat-checkbox [checked]="orderAck()" (change)="orderAck.set($event.checked)">
            Confirmo el orden de los hitos, aunque no sea T1 ≤ T2 ≤ T3
          </mat-checkbox>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando…' : correcting ? 'Corregir' : 'Registrar' }}
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
export class MilestoneDialog {
  private readonly data = inject<MilestoneDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<MilestoneDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.milestoneForm().dirty());

  protected readonly title = `${this.data.kind} · ${MILESTONE_KIND_LABELS[this.data.kind]}`;
  protected readonly correcting =
    this.data.closed === true || this.data.current.status !== 'PENDING';
  protected readonly statusLabels = MILESTONE_STATUS_LABELS;
  /** T0 siempre tiene hora y T4 solo se registra: ninguno admite «No aplica» ni «Sin dato». */
  protected readonly statuses: readonly EditableStatus[] =
    this.data.kind === 'T0' || this.data.kind === 'T4'
      ? ['RECORDED']
      : ['RECORDED', 'NOT_APPLICABLE', 'NO_DATA'];

  protected readonly model = signal<MilestoneFormValue>({
    status: this.initialStatus(),
    occurredAt: this.correcting ? localInput(this.data.current.occurredAt) : '',
    reason: '',
    justification: '',
  });

  protected readonly recorded = computed(() => this.model().status === 'RECORDED');
  protected readonly retroactive = computed(
    () => this.recorded() && isRetroactiveLocal(this.model().occurredAt),
  );
  protected readonly reasonNeeded = computed(() => !this.recorded() || this.retroactive());

  protected readonly orderWarning = signal(false);
  protected readonly orderAck = signal(false);
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly milestoneForm = form(this.model, (path) => {
    required(path.reason, {
      when: () => this.reasonNeeded(),
      message: 'Indique el motivo',
    });
    maxLength(path.reason, 300, { message: 'Máximo 300 caracteres' });
    required(path.justification, {
      when: () => this.correcting,
      message: 'Una corrección exige una justificación',
    });
    maxLength(path.justification, 300, { message: 'Máximo 300 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.milestoneForm, async () => {
      const value = this.model();
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({
          status: value.status,
          occurredAt: value.status === 'RECORDED' ? operatingInstant(value.occurredAt) : null,
          reason: this.reasonNeeded() ? value.reason.trim() : null,
          justification: this.correcting ? value.justification.trim() : null,
          orderWarningAck: this.orderAck(),
        });
        this.dialogRef.close(true);
      } catch (error) {
        const problem = toProblem(error);
        if (problem.code === ORDER_UNCONFIRMED) {
          this.orderWarning.set(true);
        }
        this.errorMessage.set(problem.detail);
      } finally {
        this.busy.set(false);
      }
    });
  }

  private initialStatus(): EditableStatus {
    const status = this.data.current.status;
    return status === 'PENDING' ? 'RECORDED' : status;
  }
}
