import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface JustificationData {
  readonly title: string;
  readonly action: string;
  readonly required: boolean;
}

/** Pide la justificacion de una accion y la devuelve al cerrar. */
@Component({
  selector: 'sarc-justification-dialog',
  imports: [FormField, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="field">
        <mat-label>Justificación</mat-label>
        <textarea matInput rows="3" [formField]="field.text"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" mat-dialog-close>Cancelar</button>
      <button
        matButton="filled"
        type="button"
        [disabled]="data.required && !model().text.trim()"
        (click)="dialog.close({ text: model().text.trim() })"
      >
        {{ data.action }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .field {
      width: min(440px, 75vw);
    }
  `,
})
export class JustificationDialog {
  protected readonly data = inject<JustificationData>(MAT_DIALOG_DATA);
  protected readonly dialog = inject(MatDialogRef);
  protected readonly model = signal({ text: '' });
  protected readonly field = form(this.model, (path) => maxLength(path.text, 500));
}
