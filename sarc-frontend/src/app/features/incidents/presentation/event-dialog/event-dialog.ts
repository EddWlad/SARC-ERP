import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { EventRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  EVENT_TYPE_LABELS,
  MANUAL_EVENT_TYPES,
  ManualEventType,
  TARGET_KIND_LABELS,
  TargetKind,
} from '../../domain/incident-labels';
import { operatingInstant } from '../../domain/incident-time';

export interface EventDialogData {
  readonly save: (body: Omit<EventRequest, 'version'>) => Promise<void>;
}

interface EventFormValue {
  type: ManualEventType;
  text: string;
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacío es el momento actual. */
  occurredAt: string;
  targetKind: TargetKind | '';
  targetRoleOrProvider: string;
}

/**
 * Registra un evento de seguimiento: validación, acción correctiva, escalamiento u observación (FR-031). Un
 * escalamiento exige su destino como lista cerrada; el rol o proveedor concreto es opcional. Los demás tipos de
 * evento los genera el sistema.
 */
@Component({
  selector: 'sarc-event-dialog',
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
    <h2 mat-dialog-title>Registrar evento</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Tipo de evento</mat-label>
          <mat-select [formField]="eventForm.type">
            @for (type of types; track type) {
              <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (escalating()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Destino del escalamiento</mat-label>
            <mat-select [formField]="eventForm.targetKind">
              @for (kind of targetKinds; track kind) {
                <mat-option [value]="kind">{{ targetLabels[kind] }}</mat-option>
              }
            </mat-select>
            @for (error of eventForm.targetKind().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Rol o proveedor</mat-label>
            <input matInput autocomplete="off" [formField]="eventForm.targetRoleOrProvider" />
            <mat-hint>Opcional.</mat-hint>
            @for (error of eventForm.targetRoleOrProvider().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Detalle</mat-label>
          <textarea matInput rows="4" [formField]="eventForm.text"></textarea>
          @for (error of eventForm.text().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Hora del hecho</mat-label>
          <input matInput type="datetime-local" [formField]="eventForm.occurredAt" />
          <mat-hint>Vacía, es el momento actual. Una hora anterior queda marcada.</mat-hint>
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando…' : 'Registrar' }}
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
export class EventDialog {
  private readonly data = inject<EventDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<EventDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.eventForm().dirty());

  protected readonly types = MANUAL_EVENT_TYPES;
  protected readonly typeLabels = EVENT_TYPE_LABELS;
  protected readonly targetKinds = Object.keys(TARGET_KIND_LABELS) as TargetKind[];
  protected readonly targetLabels = TARGET_KIND_LABELS;

  protected readonly model = signal<EventFormValue>({
    type: 'OBSERVATION',
    text: '',
    occurredAt: '',
    targetKind: '',
    targetRoleOrProvider: '',
  });
  protected readonly escalating = computed(() => this.model().type === 'ESCALATED');

  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly eventForm = form(this.model, (path) => {
    required(path.text, { message: 'Escriba el detalle del evento' });
    maxLength(path.text, 1000, { message: 'Máximo 1000 caracteres' });
    required(path.targetKind, {
      when: () => this.escalating(),
      message: 'Seleccione el destino del escalamiento',
    });
    maxLength(path.targetRoleOrProvider, 150, { message: 'Máximo 150 caracteres' });
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.eventForm, async () => {
      const value = this.model();
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({
          type: value.type,
          text: value.text.trim(),
          occurredAt: operatingInstant(value.occurredAt),
          ...(this.escalating() && value.targetKind !== ''
            ? {
                targetKind: value.targetKind,
                targetRoleOrProvider: value.targetRoleOrProvider.trim() || null,
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
