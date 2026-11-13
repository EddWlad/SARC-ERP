import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { WindowResponse } from '../../../../core/api/generated';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { WindowCloser } from '../../application/open-windows-facade';
import {
  durationText,
  formatInstant,
  localParts,
  minutesBetween,
  minutesLabel,
  operatingInstant,
} from '../../domain/intermittency-time';

/** Quien cierra llega por datos: el dialogo vive fuera del arbol que provee la fachada. */
export interface CloseWindowDialogData {
  readonly window: WindowResponse;
  readonly closer: WindowCloser;
}

/** Registra el fin de una ventana abierta. El servidor decide si queda confirmada o por revisar. */
@Component({
  selector: 'sarc-close-window-dialog',
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Registrar fin</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        <p class="target">{{ window.pointLabel }} · abierta desde {{ since }}</p>
        <div class="fields">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Fecha del fin</mat-label>
            <input
              matInput
              type="date"
              required
              [min]="startDate"
              [value]="endDate()"
              (input)="endDate.set(valueOf($event))"
            />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Hora del fin</mat-label>
            <input
              matInput
              type="time"
              required
              [value]="endTime()"
              (input)="endTime.set(valueOf($event))"
            />
          </mat-form-field>
        </div>
        @if (minutes() !== null) {
          @if (minutes()! > 0) {
            <p class="note" role="status">
              Duración: {{ durationText(minutes()!) }} · {{ minutesLabel(minutes()!) }}.
              @if (minutes()! >= 240) {
                Dura 4 horas o más: quedará por revisar.
              }
            </p>
          } @else {
            <p class="issue" role="alert">El fin debe ser posterior al inicio.</p>
          }
        }
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
        <button matButton="filled" type="submit" [disabled]="busy() || !valid()">
          Registrar fin
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
      min-width: min(420px, 75vw);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .note,
    .form-alert,
    .issue {
      margin-top: var(--sarc-space-3);
    }

    .issue {
      color: var(--mat-sys-error);
    }
  `,
})
export class CloseWindowDialog {
  private readonly data = inject<CloseWindowDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<CloseWindowDialog, WindowResponse>);

  protected readonly window = this.data.window;
  protected readonly durationText = durationText;
  protected readonly minutesLabel = minutesLabel;
  private readonly start = localParts(this.window.startAt);
  protected readonly startDate = this.start.date;
  protected readonly since = formatInstant(this.window.startAt);
  protected readonly endDate = signal(this.start.date);
  protected readonly endTime = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected readonly minutes = computed(() =>
    this.endTime()
      ? minutesBetween(this.start.date, this.start.time, this.endDate(), this.endTime())
      : null,
  );
  protected readonly valid = computed(() => (this.minutes() ?? 0) > 0);
  protected readonly unsaved = guardUnsavedChanges(() => this.endTime() !== '');

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.valid() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.ref.close(
        await this.data.closer.close(this.window, operatingInstant(this.endDate(), this.endTime())),
      );
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'No se pudo registrar el fin.');
    } finally {
      this.busy.set(false);
    }
  }
}
