import { DOCUMENT } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideCheck, LucideCopy, LucideTriangleAlert } from '@lucide/angular';

import { MessageResponse } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { MessageOptions } from '../../domain/incident-message';
import { MESSAGE_PROTOCOL_NOTE } from '../../domain/incident-labels';

export interface MessageDialogData {
  /** Pide el texto al servidor con las opciones elegidas. Solo lee: no crea eventos ni comunicaciones (FR-059). */
  readonly load: (options: MessageOptions) => Promise<MessageResponse>;
  /**
   * Abre el registro del envío real; resuelve `true` si se registró. Solo se ofrece a los editores y desde el detalle,
   * que es donde se puede actualizar el ticket.
   */
  readonly record?: () => Promise<boolean>;
}

/** Cuánto tiempo se muestra la confirmación de que el texto se copió. */
const COPIED_MS = 3000;

/**
 * Mensaje para WhatsApp (FR-055 a FR-060): el texto listo para copiar y pegar a mano, editable antes de copiarlo. Lo
 * puede abrir cualquier rol y copiar es la acción principal (dos acciones desde el ticket: abrir y copiar). Las dos
 * opciones adicionales empiezan desactivadas cada vez que se abre. Editar o copiar no cambia el ticket ni registra una
 * comunicación: SARC no envía nada, y «Registrar envío», solo para editores, confirma aparte lo que se publicó. Si falta
 * la ubicación o el enlace de Maps, se avisa con discreción y la copia no se bloquea.
 */
@Component({
  selector: 'sarc-message-dialog',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    LucideCheck,
    LucideCopy,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>Mensaje para WhatsApp</h2>
    <mat-dialog-content class="fields">
      @if (errorMessage(); as message) {
        <div class="form-alert" role="alert">
          <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
          <p>{{ message }}</p>
        </div>
      }

      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Texto del mensaje</mat-label>
        <textarea
          matInput
          rows="10"
          spellcheck="false"
          [value]="text()"
          [disabled]="loading()"
          (input)="onEdit($event)"
        ></textarea>
        <mat-hint>Puede editarlo antes de copiarlo. Editar no cambia el ticket.</mat-hint>
      </mat-form-field>

      @if (missingText(); as missing) {
        <p class="missing">{{ missing }}</p>
      }

      <fieldset class="options">
        <legend>Opciones adicionales</legend>
        <mat-checkbox
          [checked]="includeDetection()"
          [disabled]="loading()"
          (change)="setDetection($event.checked)"
        >
          Incluir hora de detección (T0)
        </mat-checkbox>
        <mat-checkbox
          [checked]="includeActions()"
          [disabled]="loading()"
          (change)="setActions($event.checked)"
        >
          Incluir acciones realizadas
        </mat-checkbox>
        <p class="hint">Cambiar una opción vuelve a generar el texto.</p>
      </fieldset>

      <p class="note">{{ protocolNote }}</p>
      <p class="sr-only" role="status" aria-live="polite">{{ copied() ? 'Texto copiado' : '' }}</p>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button matButton type="button" mat-dialog-close>Cerrar</button>
      @if (data.record) {
        <button matButton="outlined" type="button" [disabled]="loading()" (click)="record()">
          Registrar envío
        </button>
      }
      <button
        matButton="filled"
        type="button"
        cdkFocusInitial
        [disabled]="loading() || text() === ''"
        (click)="copy()"
      >
        @if (copied()) {
          <svg lucideCheck [size]="18" aria-hidden="true"></svg>
          Copiado
        } @else {
          <svg lucideCopy [size]="18" aria-hidden="true"></svg>
          Copiar
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      min-width: min(480px, 80vw);
    }

    textarea {
      font-family: var(--mat-sys-body-large-font);
    }

    .missing {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .options {
      display: flex;
      flex-direction: column;
      margin: 0;
      padding: 0;
      border: 0;

      legend {
        padding: 0;
        font: var(--mat-sys-label-large);
      }
    }

    .hint {
      margin: var(--sarc-space-1) 0 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      margin: 0;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
})
export class MessageDialog implements OnInit {
  protected readonly data = inject<MessageDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<MessageDialog>>(MatDialogRef);
  private readonly document = inject(DOCUMENT);
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected readonly protocolNote = MESSAGE_PROTOCOL_NOTE;

  /** El texto, editable. Lo que se copia es lo que se ve aquí, no lo que generó el servidor. */
  protected readonly text = signal('');
  protected readonly missingFields = signal<readonly string[]>([]);
  protected readonly includeDetection = signal(false);
  protected readonly includeActions = signal(false);
  protected readonly loading = signal(true);
  protected readonly copied = signal(false);
  protected readonly errorMessage = signal<string | undefined>(undefined);

  protected readonly missingText = computed(() => {
    const missing = this.missingFields();
    const parts = [
      missing.includes('location') ? 'la ubicación' : '',
      missing.includes('mapsUrl') ? 'el enlace de Google Maps' : '',
    ].filter((part) => part !== '');
    return parts.length === 0
      ? ''
      : `Falta ${parts.join(' y ')}: la línea queda vacía y puede completarla después de copiar.`;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  ngOnInit(): void {
    void this.reload();
  }

  protected setDetection(checked: boolean): void {
    this.includeDetection.set(checked);
    void this.reload();
  }

  protected setActions(checked: boolean): void {
    this.includeActions.set(checked);
    void this.reload();
  }

  protected onEdit(event: Event): void {
    this.text.set((event.target as HTMLTextAreaElement).value);
    this.copied.set(false);
  }

  /** Copia el texto tal como está en el cuadro. No llama al servidor: copiar no demuestra un envío (FR-059). */
  protected async copy(): Promise<void> {
    const value = this.text();
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      if (!this.copyWithSelection(value)) {
        this.errorMessage.set('No se pudo copiar. Seleccione el texto y cópielo con el teclado.');
        return;
      }
    }
    this.errorMessage.set(undefined);
    this.copied.set(true);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.copied.set(false), COPIED_MS);
  }

  protected async record(): Promise<void> {
    if (this.data.record && (await this.data.record())) {
      this.dialogRef.close();
    }
  }

  /** Plan B donde el portapapeles moderno no está disponible (por ejemplo, sin contexto seguro). */
  private copyWithSelection(value: string): boolean {
    const area = this.document.createElement('textarea');
    area.value = value;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    this.document.body.appendChild(area);
    area.select();
    try {
      return this.document.execCommand('copy');
    } catch {
      return false;
    } finally {
      area.remove();
    }
  }

  private async reload(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(undefined);
    try {
      const message = await this.data.load({
        includeDetection: this.includeDetection(),
        includeActions: this.includeActions(),
      });
      this.text.set(message.text);
      this.missingFields.set(message.missingFields);
      this.copied.set(false);
    } catch (error) {
      this.errorMessage.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }
}
