import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import {
  ExternalIdentity,
  ExternalIdentityInput,
  ExternalPlatform,
  PLATFORM_LABELS,
  PLATFORMS,
} from '../../domain/external-identity';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';

export interface ExternalIdentityDialogData {
  readonly save: (input: ExternalIdentityInput) => Promise<ExternalIdentity>;
}

interface ExternalIdentityFormValue {
  platform: ExternalPlatform | '';
  scope: string;
  externalId: string;
  alias: string;
}

@Component({
  selector: 'sarc-external-identity-dialog',
  imports: [
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>Nueva identidad externa</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">
          La marca Wallbox y la plataforma Wallbox son cosas distintas. Una URL de servidor OCPP no
          identifica por sí sola un equipo.
        </p>

        <mat-form-field appearance="outline">
          <mat-label>Plataforma</mat-label>
          <mat-select [formField]="identityForm.platform">
            @for (platform of platforms; track platform) {
              <mat-option [value]="platform">{{ platformLabels[platform] }}</mat-option>
            }
          </mat-select>
          @for (error of identityForm.platform().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Identificador en la plataforma</mat-label>
          <input matInput autocomplete="off" [formField]="identityForm.externalId" />
          @for (error of identityForm.externalId().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Ámbito</mat-label>
          <input matInput autocomplete="off" [formField]="identityForm.scope" />
          <mat-hint>Organización o cuenta dentro de la plataforma, si aplica.</mat-hint>
          @for (error of identityForm.scope().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Alias</mat-label>
          <input matInput autocomplete="off" [formField]="identityForm.alias" />
          @for (error of identityForm.alias().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando...' : 'Agregar' }}
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
      min-width: min(480px, 80vw);
    }
  `,
})
export class ExternalIdentityDialog {
  private readonly data = inject<ExternalIdentityDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<ExternalIdentityDialog, ExternalIdentity>>(MatDialogRef);
  /** Esc, clic fuera o Cancelar no descartan cambios sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.identityForm().dirty());

  protected readonly platforms = PLATFORMS;
  protected readonly platformLabels = PLATFORM_LABELS;

  protected readonly model = signal<ExternalIdentityFormValue>({
    platform: '',
    scope: '',
    externalId: '',
    alias: '',
  });
  protected readonly identityForm = form(this.model, (path) => {
    required(path.platform, { message: 'Seleccione la plataforma' });
    required(path.externalId, { message: 'Ingrese el identificador' });
    maxLength(path.externalId, 150, { message: 'Máximo 150 caracteres' });
    maxLength(path.scope, 100, { message: 'Máximo 100 caracteres' });
    maxLength(path.alias, 150, { message: 'Máximo 150 caracteres' });
  });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.identityForm, async () => {
      const value = this.model();
      if (value.platform === '') {
        return;
      }
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        this.dialogRef.close(
          await this.data.save({
            platform: value.platform,
            externalId: value.externalId.trim(),
            scope: value.scope.trim() || undefined,
            alias: value.alias.trim() || undefined,
          }),
        );
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
