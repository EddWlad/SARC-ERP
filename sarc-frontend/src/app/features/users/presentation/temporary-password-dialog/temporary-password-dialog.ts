import { Clipboard } from '@angular/cdk/clipboard';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { LucideCheck, LucideCopy } from '@lucide/angular';

export interface TemporaryPasswordDialogData {
  readonly fullName: string;
  readonly email: string;
  readonly temporaryPassword: string;
}

/** Muestra una sola vez la clave temporal; el sistema no la vuelve a exponer. */
@Component({
  selector: 'sarc-temporary-password-dialog',
  imports: [MatDialogModule, MatButtonModule, LucideCopy, LucideCheck],
  template: `
    <h2 mat-dialog-title>Contraseña temporal</h2>
    <mat-dialog-content>
      <p>
        Entregue esta contraseña a <strong>{{ data.fullName }}</strong> ({{ data.email }}) por un
        canal seguro. Deberá cambiarla al ingresar.
      </p>
      <div class="secret">
        <code aria-label="Contraseña temporal">{{ data.temporaryPassword }}</code>
        <button matButton="tonal" type="button" (click)="copy()">
          @if (copied()) {
            <svg lucideCheck [size]="18"></svg> Copiada
          } @else {
            <svg lucideCopy [size]="18"></svg> Copiar
          }
        </button>
      </div>
      <p class="warning">
        No se volverá a mostrar. Si se pierde, restablezca la contraseña otra vez.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton="filled" type="button" mat-dialog-close cdkFocusInitial>Entendido</button>
    </mat-dialog-actions>
  `,
  styles: `
    .secret {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-3);
      padding: var(--sarc-space-3);
      border-radius: var(--sarc-radius-sm);
      background: var(--mat-sys-surface-container-high);

      code {
        flex: 1;
        font-size: 1.25rem;
        letter-spacing: 0.08em;
        word-break: break-all;
      }
    }

    .warning {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class TemporaryPasswordDialog {
  private readonly clipboard = inject(Clipboard);

  protected readonly data = inject<TemporaryPasswordDialogData>(MAT_DIALOG_DATA);
  protected readonly copied = signal(false);

  protected copy(): void {
    this.copied.set(this.clipboard.copy(this.data.temporaryPassword));
  }
}
