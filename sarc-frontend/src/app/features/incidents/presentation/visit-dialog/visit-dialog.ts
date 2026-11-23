import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { TicketVisitResponse, VisitStageRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  VISIT_NEXT_STAGES,
  VISIT_STAGE_LABELS,
  VisitStage,
  visitStageNeedsDecision,
} from '../../domain/incident-labels';
import { operatingInstant } from '../../domain/incident-time';

/**
 * Lo que el diálogo entrega a quien guarda. Sin `stage` es una visita nueva (queda solicitada); con `stage`, un cambio
 * de etapa de la visita indicada en el diálogo.
 */
export type VisitDialogResult = Omit<VisitStageRequest, 'version' | 'stage'> & {
  stage?: VisitStage;
};

export interface VisitDialogData {
  /** Visita que cambia de etapa; ausente al solicitar una nueva. */
  readonly visit?: TicketVisitResponse;
  /** Proveedores ya usados, para sugerirlos al escribir (P-08). */
  readonly providers: (search: string) => Promise<string[]>;
  readonly save: (result: VisitDialogResult) => Promise<void>;
}

interface VisitFormValue {
  stage: VisitStage | '';
  providerName: string;
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacío es el momento actual. */
  occurredAt: string;
  note: string;
  cancelDecidedBy: string;
  cancelEvidenceNote: string;
  reescalationCondition: string;
}

/**
 * Solicita una visita técnica o la hace avanzar de etapa, siempre a mano (P-08): SARC no asigna ni avisa al
 * proveedor. Cancelar o aplazar exige quién decidió, la evidencia y la condición para volver a escalar (FR-035). Las
 * etapas disponibles son las que permite la transición vigente; las finales no ofrecen ninguna.
 */
@Component({
  selector: 'sarc-visit-dialog',
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
    <h2 mat-dialog-title>
      {{ visit ? 'Cambiar la etapa de la visita' : 'Solicitar visita técnica' }}
    </h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">La visita se registra a mano. SARC no la asigna ni avisa al proveedor.</p>

        @if (visit) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Nueva etapa</mat-label>
            <mat-select [formField]="visitForm.stage">
              @for (stage of nextStages; track stage) {
                <mat-option [value]="stage">{{ stageLabels[stage] }}</mat-option>
              }
            </mat-select>
            <mat-hint>Etapa actual: {{ stageLabels[visit.stage] }}</mat-hint>
            @for (error of visitForm.stage().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        @if (!visit || stage() === 'ASSIGNED') {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Proveedor</mat-label>
            <input
              matInput
              autocomplete="off"
              list="visit-providers"
              [formField]="visitForm.providerName"
            />
            <datalist id="visit-providers">
              @for (name of suggestions(); track name) {
                <option [value]="name"></option>
              }
            </datalist>
            <mat-hint>Opcional. Se sugieren los proveedores ya usados.</mat-hint>
            @for (error of visitForm.providerName().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        @if (deciding()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Quién decidió</mat-label>
            <input matInput autocomplete="off" [formField]="visitForm.cancelDecidedBy" />
            @for (error of visitForm.cancelDecidedBy().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Evidencia de la decisión</mat-label>
            <textarea matInput rows="2" [formField]="visitForm.cancelEvidenceNote"></textarea>
            <mat-hint>Dónde consta: correo, mensaje, llamada.</mat-hint>
            @for (error of visitForm.cancelEvidenceNote().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Condición para volver a escalar</mat-label>
            <input matInput autocomplete="off" [formField]="visitForm.reescalationCondition" />
            @for (error of visitForm.reescalationCondition().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Nota</mat-label>
          <textarea matInput rows="2" [formField]="visitForm.note"></textarea>
          @for (error of visitForm.note().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Hora del hecho</mat-label>
          <input matInput type="datetime-local" [formField]="visitForm.occurredAt" />
          <mat-hint>Vacía, es el momento actual. Hora de Ecuador.</mat-hint>
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button
          matButton="filled"
          type="submit"
          [disabled]="busy() || (visit && nextStages.length === 0)"
        >
          {{ busy() ? 'Guardando…' : visit ? 'Cambiar etapa' : 'Solicitar visita' }}
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
export class VisitDialog implements OnInit {
  private readonly data = inject<VisitDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<VisitDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.visitForm().dirty());

  protected readonly visit = this.data.visit;
  protected readonly stageLabels = VISIT_STAGE_LABELS;
  protected readonly nextStages: readonly VisitStage[] = this.visit
    ? VISIT_NEXT_STAGES[this.visit.stage]
    : [];

  protected readonly model = signal<VisitFormValue>({
    stage: this.nextStages[0] ?? '',
    providerName: this.visit?.providerName ?? '',
    occurredAt: '',
    note: '',
    cancelDecidedBy: '',
    cancelEvidenceNote: '',
    reescalationCondition: '',
  });
  protected readonly stage = computed(() => this.model().stage);
  protected readonly deciding = computed(() => {
    const stage = this.stage();
    return stage !== '' && visitStageNeedsDecision(stage);
  });

  protected readonly suggestions = signal<readonly string[]>([]);
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly visitForm = form(this.model, (path) => {
    required(path.stage, {
      when: () => this.visit !== undefined,
      message: 'Seleccione la nueva etapa',
    });
    maxLength(path.providerName, 120, { message: 'Máximo 120 caracteres' });
    maxLength(path.note, 500, { message: 'Máximo 500 caracteres' });
    required(path.cancelDecidedBy, {
      when: () => this.deciding(),
      message: 'Indique quién decidió',
    });
    maxLength(path.cancelDecidedBy, 150, { message: 'Máximo 150 caracteres' });
    required(path.cancelEvidenceNote, {
      when: () => this.deciding(),
      message: 'Indique la evidencia de la decisión',
    });
    maxLength(path.cancelEvidenceNote, 500, { message: 'Máximo 500 caracteres' });
    required(path.reescalationCondition, {
      when: () => this.deciding(),
      message: 'Indique la condición para volver a escalar',
    });
    maxLength(path.reescalationCondition, 300, { message: 'Máximo 300 caracteres' });
  });

  ngOnInit(): void {
    // Las sugerencias son una ayuda: si no cargan, el proveedor se escribe igual.
    this.data
      .providers('')
      .then((names) => this.suggestions.set(names))
      .catch(() => undefined);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.visitForm, async () => {
      const value = this.model();
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        await this.data.save({
          ...(this.visit && value.stage !== '' ? { stage: value.stage } : {}),
          occurredAt: operatingInstant(value.occurredAt),
          note: value.note.trim() || null,
          providerName: value.providerName.trim() || null,
          ...(this.deciding()
            ? {
                cancelDecidedBy: value.cancelDecidedBy.trim(),
                cancelEvidenceNote: value.cancelEvidenceNote.trim(),
                reescalationCondition: value.reescalationCondition.trim(),
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
