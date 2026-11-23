import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { CommunicationRequest } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  COMMUNICATION_CHANNEL_LABELS,
  COMMUNICATION_KIND_LABELS,
  CommunicationChannel,
  CommunicationKind,
  EVIDENCE_ACCEPT,
  EVIDENCE_MAX_BYTES,
  EVIDENCE_PRIVACY_REMINDER,
  fileSizeText,
} from '../../domain/incident-labels';
import { isRetroactiveLocal, localInput, operatingInstant } from '../../domain/incident-time';

export type CommunicationResult = Omit<CommunicationRequest, 'version'>;

export interface CommunicationDialogData {
  /** Tipo propuesto: el de cierre si el ticket está cerrado, el inicial si no hay avisos, o una actualización. */
  readonly suggestedKind: CommunicationKind;
  /** Guarda el envío; devuelve el motivo si la evidencia opcional no se pudo cargar (el envío ya queda registrado). */
  readonly save: (result: CommunicationResult, evidence: File | null) => Promise<string | null>;
}

interface CommunicationFormValue {
  channel: CommunicationChannel | '';
  channelDetail: string;
  kind: CommunicationKind;
  /** `aaaa-mm-ddThh:mm` en la zona operativa. */
  sentAt: string;
  justification: string;
}

/**
 * Registra el envío real de un aviso (FR-058, FR-059): después de copiar el mensaje y publicarlo a mano, el analista
 * confirma el canal, el tipo y la hora real. Copiar no registra nada; solo esta confirmación deja constancia. La hora
 * propuesta es la actual y se puede cambiar; una hora anterior exige justificación y queda marcada como retroactiva. Una
 * evidencia (por ejemplo, una captura del grupo) es opcional.
 */
@Component({
  selector: 'sarc-communication-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>Registrar envío</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }
        <p class="note">
          Registre solo lo que ya publicó. Copiar el mensaje no lo registra, y SARC no envía nada.
        </p>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Canal</mat-label>
          <mat-select [formField]="communicationForm.channel">
            @for (channel of channels; track channel) {
              <mat-option [value]="channel">{{ channelLabels[channel] }}</mat-option>
            }
          </mat-select>
          @for (error of communicationForm.channel().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        @if (other()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>¿Qué canal?</mat-label>
            <input matInput autocomplete="off" [formField]="communicationForm.channelDetail" />
            @for (error of communicationForm.channelDetail().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Tipo de aviso</mat-label>
          <mat-select [formField]="communicationForm.kind">
            @for (kind of kinds; track kind) {
              <mat-option [value]="kind">{{ kindLabels[kind] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Hora real del envío</mat-label>
          <input matInput type="datetime-local" [formField]="communicationForm.sentAt" />
          <mat-hint>Propuesta: ahora. Hora de Ecuador.</mat-hint>
          @for (error of communicationForm.sentAt().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        @if (retroactive()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Justificación de la hora anterior</mat-label>
            <input matInput autocomplete="off" [formField]="communicationForm.justification" />
            @for (error of communicationForm.justification().errors(); track error.kind) {
              <mat-error>{{ error.message }}</mat-error>
            }
          </mat-form-field>
        }

        <div class="evidence">
          <span class="evidence-title">Evidencia (opcional)</span>
          <p class="hint">{{ privacyReminder }}</p>
          <div class="evidence-controls">
            <input
              #picker
              class="visually-hidden"
              type="file"
              tabindex="-1"
              aria-hidden="true"
              [accept]="accept"
              (change)="onPicked(picker)"
            />
            <button matButton="tonal" type="button" (click)="picker.click()">
              {{ file() ? 'Cambiar archivo' : 'Adjuntar captura' }}
            </button>
            @if (file(); as chosen) {
              <span class="file-name">{{ chosen.name }} · {{ fileSize(chosen.size) }}</span>
              <button matButton type="button" (click)="file.set(null)">Quitar</button>
            }
          </div>
          @if (fileError(); as message) {
            <p class="file-error" role="alert">{{ message }}</p>
          }
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy()">
          {{ busy() ? 'Guardando…' : 'Registrar envío' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      min-width: min(480px, 80vw);
    }

    .evidence {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
    }

    .evidence-title {
      font: var(--mat-sys-label-large);
    }

    .evidence-controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-2) var(--sarc-space-3);
    }

    .hint {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .file-name {
      overflow-wrap: anywhere;
    }

    .file-error {
      margin: 0;
      color: var(--mat-sys-error);
    }

    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
})
export class CommunicationDialog {
  private readonly data = inject<CommunicationDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<CommunicationDialog, string | null>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.communicationForm().dirty());

  protected readonly channels = Object.keys(COMMUNICATION_CHANNEL_LABELS) as CommunicationChannel[];
  protected readonly channelLabels = COMMUNICATION_CHANNEL_LABELS;
  protected readonly kinds = Object.keys(COMMUNICATION_KIND_LABELS) as CommunicationKind[];
  protected readonly kindLabels = COMMUNICATION_KIND_LABELS;
  protected readonly accept = EVIDENCE_ACCEPT;
  protected readonly privacyReminder = EVIDENCE_PRIVACY_REMINDER;
  protected readonly fileSize = fileSizeText;

  /** La hora propuesta, redondeada al minuto. Mientras no se cambie, el envío es «ahora» y se manda la hora del envío. */
  private readonly proposedSentAt = localInput(new Date());

  protected readonly model = signal<CommunicationFormValue>({
    channel: '',
    channelDetail: '',
    kind: this.data.suggestedKind,
    sentAt: this.proposedSentAt,
    justification: '',
  });
  protected readonly other = computed(() => this.model().channel === 'OTHER');
  /**
   * Solo una hora que el analista cambió puede ser retroactiva. La propuesta es el minuto actual redondeado hacia abajo:
   * si pasan unos segundos antes de guardar, no debe pasar a ser «anterior» y pedir una justificación que no se ve.
   */
  protected readonly retroactive = computed(
    () => this.model().sentAt !== this.proposedSentAt && isRetroactiveLocal(this.model().sentAt),
  );

  protected readonly file = signal<File | null>(null);
  protected readonly fileError = signal('');
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly communicationForm = form(this.model, (path) => {
    required(path.channel, { message: 'Seleccione el canal' });
    required(path.channelDetail, { when: () => this.other(), message: 'Indique qué canal fue' });
    maxLength(path.channelDetail, 150, { message: 'Máximo 150 caracteres' });
    required(path.sentAt, { message: 'Indique la hora real del envío' });
    required(path.justification, {
      when: () => this.retroactive(),
      message: 'Una hora anterior exige una justificación',
    });
    maxLength(path.justification, 300, { message: 'Máximo 300 caracteres' });
  });

  protected onPicked(input: HTMLInputElement): void {
    const chosen = input.files?.item(0);
    input.value = '';
    if (!chosen) return;
    if (chosen.size === 0) {
      this.fileError.set('El archivo está vacío.');
    } else if (chosen.size > EVIDENCE_MAX_BYTES) {
      this.fileError.set('El archivo supera el límite de 10 MB.');
    } else {
      this.fileError.set('');
      this.file.set(chosen);
    }
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.communicationForm, async () => {
      const value = this.model();
      const sentAt =
        value.sentAt === this.proposedSentAt
          ? new Date().toISOString()
          : operatingInstant(value.sentAt);
      if (value.channel === '' || sentAt === null) return;
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        const warning = await this.data.save(
          {
            channel: value.channel,
            channelDetail: value.channel === 'OTHER' ? value.channelDetail.trim() : null,
            kind: value.kind,
            sentAt,
            justification: this.retroactive() ? value.justification.trim() : null,
          },
          this.file(),
        );
        this.dialogRef.close(warning);
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
