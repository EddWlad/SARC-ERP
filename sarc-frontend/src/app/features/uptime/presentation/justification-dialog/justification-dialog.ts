import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { toOperatingInstant } from '../../domain/uptime-time';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { MatInputModule } from '@angular/material/input';

export interface JustificationData {
  readonly title: string;
  readonly action: string;
  readonly required: boolean;
  readonly temporal?: { referenceWeek?: string | null };
  readonly submitTemporal?: (request: {
    temporalPrecision: 'REFERENCE_WEEK' | 'KNOWN_RANGE';
    referenceWeek?: string;
    periodStart?: string;
    periodEnd?: string;
    justification: string;
    evidence: string;
  }) => Promise<boolean>;
  readonly error?: () => string;
}

/** Pide la justificacion de una accion y la devuelve al cerrar. */
@Component({
  selector: 'sarc-justification-dialog',
  imports: [
    MatSelectModule,
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic">
        <mat-label>Justificación</mat-label>
        <textarea matInput rows="3" [formField]="field.text"></textarea>
      </mat-form-field>
      @if (data.temporal) {
        <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic"
          ><mat-label>Tratamiento temporal</mat-label>
          <mat-select [formField]="field.precision"
            ><mat-option value="REFERENCE_WEEK">Informe sin horario real</mat-option
            ><mat-option value="KNOWN_RANGE">Rango real acreditado</mat-option></mat-select
          >
        </mat-form-field>
        <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic"
          ><mat-label>Semana de referencia (lunes)</mat-label
          ><input matInput type="date" [formField]="field.week"
        /></mat-form-field>
        @if (model().precision === 'KNOWN_RANGE') {
          <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic"
            ><mat-label>Desde real · Ecuador</mat-label
            ><input matInput type="datetime-local" step="1" [formField]="field.start"
          /></mat-form-field>
          <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic"
            ><mat-label>Hasta real · Ecuador</mat-label
            ><input matInput type="datetime-local" step="1" [formField]="field.end"
          /></mat-form-field>
        }
        <mat-form-field appearance="outline" class="field" subscriptSizing="dynamic"
          ><mat-label>Evidencia que respalda la decisión</mat-label
          ><textarea matInput rows="3" [formField]="field.evidence"></textarea>
        </mat-form-field>
        <p>
          Se conservan los valores, validaciones e historial. Una colisión requiere conciliación
          explícita.
        </p>
        @if (data.error?.(); as error) {
          <p role="alert">{{ error }}</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" (click)="cancel()">Cancelar</button>
      <button
        matButton="filled"
        type="button"
        [disabled]="
          submitting() ||
          (data.required && !model().text.trim()) ||
          (data.temporal &&
            (!model().evidence.trim() ||
              (model().precision === 'REFERENCE_WEEK'
                ? !model().week
                : !model().start || !model().end)))
        "
        (click)="submit()"
      >
        {{ data.action }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    // Con ayuda dinamica los campos no reservan espacio: la separacion la pone el contenedor.
    mat-dialog-content {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
    }

    .field {
      width: min(440px, 75vw);
    }

    p {
      margin: 0;
    }
  `,
})
export class JustificationDialog {
  protected readonly data = inject<JustificationData>(MAT_DIALOG_DATA);
  protected readonly dialog = inject(MatDialogRef);
  protected readonly submitting = signal(false);
  protected readonly model = signal({
    text: '',
    evidence: '',
    precision: 'REFERENCE_WEEK' as 'REFERENCE_WEEK' | 'KNOWN_RANGE',
    week: this.data.temporal?.referenceWeek ?? '',
    start: '',
    end: '',
  });
  protected readonly field = form(this.model, (path) => {
    if (!this.data.temporal) maxLength(path.text, 500);
  });
  private readonly unsaved = this.data.temporal
    ? guardUnsavedChanges(() => this.field().dirty())
    : null;

  protected cancel(): void {
    if (this.submitting()) return;
    if (this.unsaved) void this.unsaved.cancel();
    else this.dialog.close();
  }

  protected async submit(): Promise<void> {
    if (!this.data.temporal) {
      this.dialog.close({ text: this.model().text.trim() });
      return;
    }
    const m = this.model();
    this.submitting.set(true);
    try {
      const accepted = await this.data.submitTemporal?.({
        temporalPrecision: m.precision,
        referenceWeek: m.week || undefined,
        periodStart: m.precision === 'KNOWN_RANGE' ? toOperatingInstant(m.start) : undefined,
        periodEnd: m.precision === 'KNOWN_RANGE' ? toOperatingInstant(m.end) : undefined,
        justification: m.text.trim(),
        evidence: m.evidence.trim(),
      });
      if (accepted) this.dialog.close({ accepted: true });
    } finally {
      this.submitting.set(false);
    }
  }
}
