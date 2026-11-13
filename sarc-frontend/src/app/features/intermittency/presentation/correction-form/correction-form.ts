import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { CorrectWindowRequest, WindowResponse } from '../../../../core/api/generated';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  durationText,
  formatInstant,
  localParts,
  minutesBetween,
  minutesLabel,
  operatingInstant,
  todayInOperatingZone,
} from '../../domain/intermittency-time';
import { JUSTIFICATION_MAX } from '../justification-dialog/justification-dialog';

/** Una ventana cerrada: solo estas se corrigen; el servidor rechaza corregir una abierta. */
export type ClosedWindow = WindowResponse & { readonly endAt: string };

export function isClosed(window: WindowResponse): window is ClosedWindow {
  return !!window.endAt;
}

export interface CorrectionFormData {
  readonly window: ClosedWindow;
  /** Envia la correccion; si falla, el formulario sigue abierto con el mensaje y conserva lo escrito. */
  readonly submit: (body: Omit<CorrectWindowRequest, 'version'>) => Promise<unknown>;
}

/**
 * Corrige el inicio, el fin o el comentario de una ventana cerrada. Los instantes llevan fecha y hora
 * completas, la duracion se recalcula en vivo y la justificacion es obligatoria (FR-017, FR-021a).
 */
@Component({
  selector: 'sarc-correction-form',
  imports: [FormField, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Corregir la ventana</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        <p class="target">{{ window.pointLabel }} · {{ original }}</p>
        <div class="fields">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Fecha del inicio</mat-label>
            <input matInput type="date" [max]="today" [formField]="field.startDate" />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Hora del inicio</mat-label>
            <input matInput type="time" [formField]="field.startTime" />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Fecha del fin</mat-label>
            <input matInput type="date" [max]="today" [formField]="field.endDate" />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Hora del fin</mat-label>
            <input matInput type="time" [formField]="field.endTime" />
          </mat-form-field>
        </div>

        @if (minutes() !== null && minutes()! > 0) {
          <p class="note" role="status">
            Duración: {{ durationText(minutes()!) }} · {{ minutesLabel(minutes()!) }}.
            @if (minutes()! >= 240) {
              Dura 4 horas o más: quedará por revisar.
            }
          </p>
        } @else {
          <p class="issue" role="alert">El fin debe ser posterior al inicio.</p>
        }

        <mat-form-field appearance="outline" class="wide" subscriptSizing="dynamic">
          <mat-label>Comentario</mat-label>
          <textarea matInput rows="2" [formField]="field.comment"></textarea>
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide" subscriptSizing="dynamic">
          <mat-label>Justificación de la corrección</mat-label>
          <textarea matInput rows="3" [formField]="field.justification"></textarea>
          <mat-hint align="end">{{ model().justification.length }}/{{ max }}</mat-hint>
        </mat-form-field>
        <p class="note">Se conserva la versión anterior en el historial de la ventana.</p>

        @if (error()) {
          <div class="form-alert" role="alert">
            <p>{{ error() }}</p>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy() || !canSave()">
          Guardar corrección
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .target {
      margin: 0 0 var(--sarc-space-3);
      font: var(--mat-sys-title-small);
      overflow-wrap: anywhere;
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3);
      min-width: min(480px, 75vw);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .wide {
      display: block;
      margin-top: var(--sarc-space-3);
    }

    .note,
    .issue,
    .form-alert {
      margin-top: var(--sarc-space-3);
    }

    .issue {
      color: var(--mat-sys-error);
    }
  `,
})
export class CorrectionForm {
  private readonly data = inject<CorrectionFormData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<CorrectionForm, unknown>);

  protected readonly window = this.data.window;
  private readonly start = localParts(this.window.startAt);
  private readonly end = localParts(this.window.endAt);
  protected readonly original = `${formatInstant(this.window.startAt)} a ${formatInstant(this.window.endAt)}`;
  protected readonly today = todayInOperatingZone();
  protected readonly max = JUSTIFICATION_MAX;
  protected readonly durationText = durationText;
  protected readonly minutesLabel = minutesLabel;

  protected readonly model = signal({
    startDate: this.start.date,
    startTime: this.start.time,
    endDate: this.end.date,
    endTime: this.end.time,
    comment: this.window.comment ?? '',
    justification: '',
  });
  protected readonly field = form(this.model, (path) => {
    maxLength(path.comment, 500);
    maxLength(path.justification, JUSTIFICATION_MAX);
  });
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected readonly minutes = computed(() => {
    const value = this.model();
    return minutesBetween(value.startDate, value.startTime, value.endDate, value.endTime);
  });
  private readonly startChanged = computed(
    () => this.model().startDate !== this.start.date || this.model().startTime !== this.start.time,
  );
  private readonly endChanged = computed(
    () => this.model().endDate !== this.end.date || this.model().endTime !== this.end.time,
  );
  private readonly commentChanged = computed(
    () => this.model().comment.trim() !== (this.window.comment ?? ''),
  );
  protected readonly changed = computed(
    () => this.startChanged() || this.endChanged() || this.commentChanged(),
  );
  protected readonly canSave = computed(() => {
    const value = this.model();
    return (
      this.changed() &&
      value.justification.trim() !== '' &&
      value.justification.length <= JUSTIFICATION_MAX &&
      value.comment.length <= 500 &&
      (this.minutes() ?? 0) > 0
    );
  });
  protected readonly unsaved = guardUnsavedChanges(
    () => this.changed() || this.model().justification !== '',
  );

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.canSave() || this.busy()) return;
    const value = this.model();
    const body: Omit<CorrectWindowRequest, 'version'> = {
      ...(this.startChanged()
        ? { startAt: operatingInstant(value.startDate, value.startTime) }
        : {}),
      ...(this.endChanged() ? { endAt: operatingInstant(value.endDate, value.endTime) } : {}),
      ...(this.commentChanged() ? { comment: value.comment.trim() } : {}),
      justification: value.justification.trim(),
    };
    this.busy.set(true);
    this.error.set('');
    try {
      this.ref.close((await this.data.submit(body)) ?? true);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'No se pudo guardar la corrección.');
    } finally {
      this.busy.set(false);
    }
  }
}
