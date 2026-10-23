import { Component, computed, inject, signal } from '@angular/core';
import {
  applyEach,
  form,
  FormField,
  maxLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucidePlus, LucideTrash2, LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import { Equipment } from '../../domain/equipment';
import {
  DAY_LABELS,
  DAYS,
  Schedule,
  SCHEDULE_MODE_LABELS,
  SCHEDULE_MODES,
  ScheduleInput,
} from '../../domain/schedule';
import {
  initialScheduleValue,
  newException,
  newInterval,
  ScheduleFormValue,
  toScheduleInput,
} from './schedule-form';

export interface ScheduleDialogData {
  readonly schedule: Schedule;
  /** Persiste el horario. Si falla, el dialogo sigue abierto y muestra el error. */
  readonly save: (input: Omit<ScheduleInput, 'version'>) => Promise<Equipment>;
}

const SAME_TIME = { kind: 'sameTime', message: 'La apertura y el cierre no pueden ser iguales' };

/** Editor del horario: modo, intervalos por dia (un cierre anterior a la apertura cruza la medianoche) y excepciones. */
@Component({
  selector: 'sarc-schedule-dialog',
  imports: [
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucidePlus,
    LucideTrash2,
    LucideTriangleAlert,
  ],
  templateUrl: './schedule-dialog.html',
  styleUrl: './schedule-dialog.scss',
})
export class ScheduleDialog {
  private readonly data = inject<ScheduleDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ScheduleDialog, Equipment>>(MatDialogRef);

  protected readonly modes = SCHEDULE_MODES;
  protected readonly modeLabels = SCHEDULE_MODE_LABELS;
  protected readonly days = DAYS;
  protected readonly dayLabels = DAY_LABELS;

  protected readonly model = signal<ScheduleFormValue>(initialScheduleValue(this.data.schedule));
  protected readonly weekly = computed(() => this.model().mode === 'WEEKLY');

  protected readonly scheduleForm = form(this.model, (path) => {
    validate(path.intervals, ({ value }) =>
      this.weekly() && value().length === 0
        ? { kind: 'noIntervals', message: 'Agregue al menos un intervalo' }
        : undefined,
    );
    applyEach(path.intervals, (interval) => {
      required(interval.opensAt, { message: 'Ingrese la hora' });
      required(interval.closesAt, { message: 'Ingrese la hora' });
      validate(interval.closesAt, ({ value, valueOf }) =>
        value() !== '' && value() === valueOf(interval.opensAt) ? SAME_TIME : undefined,
      );
    });
    applyEach(path.exceptions, (exception) => {
      required(exception.date, { message: 'Ingrese la fecha' });
      required(exception.opensAt, {
        message: 'Ingrese la hora',
        when: ({ valueOf }) => valueOf(exception.kind) === 'HOURS',
      });
      required(exception.closesAt, {
        message: 'Ingrese la hora',
        when: ({ valueOf }) => valueOf(exception.kind) === 'HOURS',
      });
      validate(exception.closesAt, ({ value, valueOf }) =>
        valueOf(exception.kind) === 'HOURS' &&
        value() !== '' &&
        value() === valueOf(exception.opensAt)
          ? SAME_TIME
          : undefined,
      );
      maxLength(exception.note, 200, { message: 'Máximo 200 caracteres' });
    });
  });

  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected addInterval(): void {
    this.model.update((value) => ({ ...value, intervals: [...value.intervals, newInterval()] }));
  }

  protected removeInterval(index: number): void {
    this.model.update((value) => ({
      ...value,
      intervals: value.intervals.filter((_, position) => position !== index),
    }));
  }

  protected addException(): void {
    this.model.update((value) => ({ ...value, exceptions: [...value.exceptions, newException()] }));
  }

  protected removeException(index: number): void {
    this.model.update((value) => ({
      ...value,
      exceptions: value.exceptions.filter((_, position) => position !== index),
    }));
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.scheduleForm, async () => {
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        this.dialogRef.close(await this.data.save(toScheduleInput(this.model())));
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
