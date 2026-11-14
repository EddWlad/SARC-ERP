import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';

import { KiaPointResponse } from '../../../../core/api/generated';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { PointTestPreview } from '../../application/review-facade';
import { formatDay, todayInOperatingZone } from '../../domain/intermittency-time';
import { JUSTIFICATION_MAX } from '../justification-dialog/justification-dialog';
import { PointPicker } from '../point-picker/point-picker';

/** Lo que el dialogo necesita de la fachada de revision. */
export interface PointTestActions {
  previewPointTest(
    point: KiaPointResponse,
    firstDay: string,
    lastDay: string,
  ): Promise<PointTestPreview>;
  pointTest(
    point: KiaPointResponse,
    mark: boolean,
    firstDay: string,
    lastDay: string,
    justification: string,
  ): Promise<{ affectedWindows: number; openWindowsNotMarked: number }>;
}

export interface PointTestDialogData {
  readonly actions: PointTestActions;
}

/**
 * Marca o quita la prueba de las ventanas de un punto en un rango de dias. Antes de confirmar cuenta las
 * ventanas que se marcarian y las abiertas que quedarian sin marcar; despues muestra lo que informo el
 * servidor (FR-016).
 */
@Component({
  selector: 'sarc-point-test-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    PointPicker,
  ],
  template: `
    <h2 mat-dialog-title>Marcar prueba por punto</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        @if (result(); as done) {
          <p class="note" role="status">
            @if (mark()) {
              Se marcaron {{ done.affectedWindows }}
              {{ done.affectedWindows === 1 ? 'ventana' : 'ventanas' }} como prueba.
            } @else {
              Se quitó la prueba a {{ done.affectedWindows }}
              {{ done.affectedWindows === 1 ? 'ventana' : 'ventanas' }}.
            }
            @if (done.openWindowsNotMarked > 0) {
              {{ done.openWindowsNotMarked }}
              {{
                done.openWindowsNotMarked === 1
                  ? 'ventana abierta quedó'
                  : 'ventanas abiertas quedaron'
              }}
              sin marcar: una ventana de prueba debe estar cerrada.
            }
          </p>
        } @else {
          <sarc-point-picker [point]="point()" (pointChange)="point.set($event)" />
          <mat-radio-group
            class="action"
            aria-label="Acción"
            [value]="mark()"
            (change)="mark.set($event.value)"
          >
            <mat-radio-button [value]="true">Marcar como prueba</mat-radio-button>
            <mat-radio-button [value]="false">Quitar la prueba</mat-radio-button>
          </mat-radio-group>
          <div class="fields">
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Primer día</mat-label>
              <input matInput type="date" [max]="today" [formField]="field.firstDay" />
            </mat-form-field>
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Último día (incluido)</mat-label>
              <input
                matInput
                type="date"
                [min]="model().firstDay"
                [max]="today"
                [formField]="field.lastDay"
              />
            </mat-form-field>
          </div>
          @if (rangeInvalid()) {
            <p class="issue" role="alert">El último día no puede ser anterior al primero.</p>
          } @else if (previewError()) {
            <p class="issue" role="alert">{{ previewError() }}</p>
          } @else if (mark() && preview(); as counts) {
            <p class="note" role="status">
              Se marcarán {{ counts.willMark }}
              {{ counts.willMark === 1 ? 'ventana' : 'ventanas' }} del
              {{ day(model().firstDay) }} al {{ day(model().lastDay) }}.
              @if (counts.openNotMarked > 0) {
                {{ counts.openNotMarked }}
                {{
                  counts.openNotMarked === 1
                    ? 'ventana abierta quedará'
                    : 'ventanas abiertas quedarán'
                }}
                sin marcar hasta que se cierren.
              }
            </p>
          }
          <mat-form-field
            floatLabel="always"
            appearance="outline"
            class="wide"
            subscriptSizing="dynamic"
          >
            <mat-label>Justificación</mat-label>
            <textarea matInput rows="3" [formField]="field.justification"></textarea>
            <mat-hint align="end">{{ model().justification.length }}/{{ max }}</mat-hint>
          </mat-form-field>
        }
        @if (error()) {
          <div class="form-alert" role="alert">
            <p>{{ error() }}</p>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        @if (result()) {
          <button matButton="filled" type="button" (click)="done()">Cerrar</button>
        } @else {
          <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
            Cancelar
          </button>
          <button matButton="filled" type="submit" [disabled]="busy() || !canSave()">
            {{ mark() ? 'Marcar prueba' : 'Quitar prueba' }}
          </button>
        }
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    :host {
      display: block;
      min-width: min(480px, 75vw);
    }

    .action {
      display: flex;
      flex-direction: column;
      margin: var(--sarc-space-3) 0;
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3);

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
export class PointTestDialog {
  private readonly data = inject<PointTestDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PointTestDialog, boolean>);

  protected readonly today = todayInOperatingZone();
  protected readonly max = JUSTIFICATION_MAX;
  protected readonly day = formatDay;
  protected readonly point = signal<KiaPointResponse | null>(null);
  protected readonly mark = signal(true);
  protected readonly model = signal({ firstDay: '', lastDay: '', justification: '' });
  protected readonly field = form(this.model, (path) => {
    maxLength(path.justification, JUSTIFICATION_MAX);
  });
  protected readonly preview = signal<PointTestPreview | null>(null);
  protected readonly previewError = signal('');
  protected readonly result = signal<{
    affectedWindows: number;
    openWindowsNotMarked: number;
  } | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected readonly rangeInvalid = computed(() => {
    const { firstDay, lastDay } = this.model();
    return !!firstDay && !!lastDay && lastDay < firstDay;
  });
  // Marcar exige haber visto antes cuantas ventanas se marcan (FR-016): sin el conteo no se confirma.
  protected readonly canSave = computed(() => {
    const { firstDay, lastDay, justification } = this.model();
    return (
      !!this.point() &&
      !!firstDay &&
      !!lastDay &&
      !this.rangeInvalid() &&
      justification.trim() !== '' &&
      justification.length <= JUSTIFICATION_MAX &&
      (!this.mark() || this.preview() !== null)
    );
  });
  protected readonly unsaved = guardUnsavedChanges(
    () => this.result() === null && (this.point() !== null || this.model().justification !== ''),
  );
  private ticket = 0;

  constructor() {
    // El conteo previo se pide al cambiar punto o rango; una respuesta tardia no pisa a la actual.
    effect(() => {
      const point = this.point();
      const { firstDay: first, lastDay: last } = this.model();
      const invalid = this.rangeInvalid();
      const ticket = ++this.ticket;
      untracked(() => {
        this.preview.set(null);
        this.previewError.set('');
      });
      if (!point || !first || !last || invalid) return;
      void this.data.actions.previewPointTest(point, first, last).then(
        (counts) => {
          if (ticket === this.ticket) this.preview.set(counts);
        },
        (error: unknown) => {
          if (ticket !== this.ticket) return;
          this.previewError.set(
            error instanceof Error
              ? error.message
              : 'No se pudo contar las ventanas que se marcarían; cambie el rango para reintentar.',
          );
        },
      );
    });
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    const point = this.point();
    if (!point || !this.canSave() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.result.set(
        await this.data.actions.pointTest(
          point,
          this.mark(),
          this.model().firstDay,
          this.model().lastDay,
          this.model().justification.trim(),
        ),
      );
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'No se pudo cambiar la prueba.');
    } finally {
      this.busy.set(false);
    }
  }

  protected done(): void {
    this.ref.close(true);
  }
}
