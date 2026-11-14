import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';

/** Maximo que acepta el servidor para una justificacion. */
export const JUSTIFICATION_MAX = 500;

export interface JustificationDialogData {
  readonly title: string;
  /** Texto del boton que confirma. */
  readonly action: string;
  /** Linea que dice sobre que se actua, por ejemplo el punto y el horario. */
  readonly target?: string;
  /** Aviso adicional, por ejemplo lo que no cambia. */
  readonly hint?: string;
  /**
   * Ejecuta el cambio con la justificacion. Si falla, el dialogo sigue abierto con el mensaje y conserva
   * lo escrito; si sale bien, se cierra devolviendo el resultado.
   */
  readonly submit: (justification: string) => Promise<unknown>;
}

/** Pide la justificacion obligatoria de un cambio sobre una ventana (FR-017); mismo diseno que Uptime. */
@Component({
  selector: 'sarc-justification-dialog',
  imports: [FormField, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content>
        @if (data.target) {
          <p class="target">{{ data.target }}</p>
        }
        <mat-form-field
          floatLabel="always"
          appearance="outline"
          class="field"
          subscriptSizing="dynamic"
        >
          <mat-label>Justificación</mat-label>
          <textarea matInput rows="3" [formField]="field.text"></textarea>
          <mat-hint align="end">{{ model().text.length }}/{{ max }}</mat-hint>
        </mat-form-field>
        @if (data.hint) {
          <p class="note">{{ data.hint }}</p>
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
          {{ data.action }}
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

    .field {
      width: min(480px, 75vw);
    }

    .note,
    .form-alert {
      margin-top: var(--sarc-space-3);
    }
  `,
})
export class JustificationDialog {
  protected readonly data = inject<JustificationDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<JustificationDialog, unknown>);

  protected readonly max = JUSTIFICATION_MAX;
  protected readonly model = signal({ text: '' });
  protected readonly field = form(this.model, (path) => {
    maxLength(path.text, JUSTIFICATION_MAX);
  });
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly valid = computed(
    () => this.model().text.trim() !== '' && this.model().text.length <= JUSTIFICATION_MAX,
  );
  protected readonly unsaved = guardUnsavedChanges(() => this.model().text !== '');

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.valid() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.ref.close((await this.data.submit(this.model().text.trim())) ?? true);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'No se pudo guardar el cambio.');
    } finally {
      this.busy.set(false);
    }
  }
}
