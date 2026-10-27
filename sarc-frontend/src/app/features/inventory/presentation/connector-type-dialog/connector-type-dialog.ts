import { Component, inject, signal } from '@angular/core';
import {
  disabled,
  form,
  FormField,
  maxLength,
  pattern,
  required,
  submit,
} from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import { ConnectorType } from '../../domain/connector-type';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';

export interface ConnectorTypeFormValue {
  code: string;
  name: string;
}

export interface ConnectorTypeDialogData {
  /** Tipo a renombrar; el codigo no cambia porque lo usan los historicos. */
  readonly connectorType?: ConnectorType;
  readonly save: (value: ConnectorTypeFormValue) => Promise<ConnectorType>;
}

@Component({
  selector: 'sarc-connector-type-dialog',
  imports: [
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ editing ? 'Renombrar tipo de conector' : 'Nuevo tipo de conector' }}
    </h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <mat-form-field appearance="outline">
          <mat-label>Código</mat-label>
          <input matInput autocomplete="off" [formField]="typeForm.code" />
          <mat-hint>{{
            editing
              ? 'El código no cambia.'
              : 'Letras, números y guion bajo; se guarda en mayúsculas (p. ej. CCS2).'
          }}</mat-hint>
          @for (error of typeForm.code().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Nombre</mat-label>
          <input matInput autocomplete="off" [formField]="typeForm.name" />
          @for (error of typeForm.name().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando...' : editing ? 'Guardar' : 'Crear' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      min-width: min(420px, 80vw);
    }
  `,
})
export class ConnectorTypeDialog {
  private readonly data = inject<ConnectorTypeDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<ConnectorTypeDialog, ConnectorType>>(MatDialogRef);
  /** Esc, clic fuera o Cancelar no descartan cambios sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.typeForm().dirty());

  protected readonly editing = this.data.connectorType !== undefined;
  protected readonly model = signal<ConnectorTypeFormValue>({
    code: this.data.connectorType?.code ?? '',
    name: this.data.connectorType?.name ?? '',
  });
  protected readonly typeForm = form(this.model, (path) => {
    required(path.code, { message: 'Ingrese el código' });
    maxLength(path.code, 30, { message: 'Máximo 30 caracteres' });
    pattern(path.code, /^[A-Za-z0-9_ ]+$/, { message: 'Solo letras, números y guion bajo' });
    disabled(path.code, { when: () => this.editing });
    required(path.name, { message: 'Ingrese el nombre' });
    maxLength(path.name, 100, { message: 'Máximo 100 caracteres' });
  });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.typeForm, async () => {
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        const { code, name } = this.model();
        this.dialogRef.close(await this.data.save({ code: code.trim(), name: name.trim() }));
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
