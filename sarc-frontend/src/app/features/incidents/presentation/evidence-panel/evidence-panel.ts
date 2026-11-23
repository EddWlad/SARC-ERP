import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { LucideFileText, LucideImage, LucideTriangleAlert } from '@lucide/angular';

import { TicketEvidenceResponse } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { TicketDetailFacade } from '../../application/ticket-detail-facade';
import {
  EVIDENCE_ACCEPT,
  EVIDENCE_MAX_BYTES,
  EVIDENCE_PRIVACY_REMINDER,
  EVIDENCE_PURPOSE_LABELS,
  EvidencePurpose,
  fileSizeText,
} from '../../domain/incident-labels';
import { formatInstant } from '../../domain/incident-time';

/**
 * Evidencias del ticket: imágenes JPEG, PNG o WEBP, y PDF, hasta 10 MB (FR-037). Una evidencia no se borra; si se
 * cargó por error se anula su evento desde la cronología. Se descarga con una redirección a una URL firmada. Junto a
 * la carga va siempre el recordatorio de minimizar los datos personales de terceros en las capturas.
 */
@Component({
  selector: 'sarc-evidence-panel',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    LucideFileText,
    LucideImage,
    LucideTriangleAlert,
  ],
  template: `
    <section class="panel" aria-labelledby="evidence-title">
      <div class="panel-heading">
        <h2 id="evidence-title">Evidencias</h2>
      </div>

      @if (evidence().length === 0) {
        <p class="muted">Este ticket no tiene evidencias.</p>
      } @else {
        <ul class="evidence">
          @for (item of evidence(); track item.id) {
            <li [class.annulled]="item.annulled">
              @if (item.format === 'PDF') {
                <svg lucideFileText [size]="20" aria-hidden="true"></svg>
              } @else {
                <svg lucideImage [size]="20" aria-hidden="true"></svg>
              }
              <div class="info">
                <a [href]="url(item)" target="_blank" rel="noopener noreferrer">{{
                  item.originalName
                }}</a>
                <span class="muted">
                  {{ item.format }} · {{ size(item.sizeBytes) }} ·
                  {{ purposeLabels[asPurpose(item.purpose)] }} · {{ instant(item.uploadedAt) }}
                  @if (item.uploadedByName) {
                    · {{ item.uploadedByName }}
                  }
                  @if (item.annulled) {
                    · anulada
                  }
                </span>
                <span class="muted hash" [attr.title]="item.sha256"
                  >SHA-256 {{ item.sha256.slice(0, 12) }}…</span
                >
              </div>
            </li>
          }
        </ul>
      }

      @if (editable()) {
        <div class="upload">
          <p class="note">{{ reminder }}</p>
          @if (errorMessage(); as message) {
            <div class="form-alert" role="alert">
              <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
              <p>{{ message }}</p>
            </div>
          }
          <div class="controls">
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Uso de la evidencia</mat-label>
              <mat-select [value]="purpose()" (selectionChange)="purpose.set($event.value)">
                @for (option of purposes; track option) {
                  <mat-option [value]="option">{{ purposeLabels[option] }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <input
              #picker
              class="visually-hidden"
              type="file"
              tabindex="-1"
              aria-hidden="true"
              [accept]="accept"
              (change)="onPicked(picker)"
            />
            <button matButton="tonal" type="button" [disabled]="busy()" (click)="picker.click()">
              {{ busy() ? 'Cargando…' : 'Adjuntar evidencia' }}
            </button>
          </div>
          <p class="muted limits">Imágenes JPEG, PNG o WEBP, o PDF. Hasta 10 MB.</p>
        </div>
      }
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';
    @use '../detail-panel';

    :host {
      display: block;
    }

    .evidence {
      margin: 0 0 var(--sarc-space-3);
      padding: 0;
      list-style: none;

      li {
        display: flex;
        gap: var(--sarc-space-3);
        padding: var(--sarc-space-2) 0;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }

      li:last-child {
        border-bottom: 0;
      }

      .annulled a {
        text-decoration: line-through;
      }
    }

    .info {
      display: flex;
      flex-direction: column;
      min-width: 0;
      overflow-wrap: anywhere;

      a {
        color: var(--mat-sys-primary);
      }
    }

    .hash {
      font: var(--mat-sys-body-small);
    }

    .upload {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
    }

    .controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-3);

      mat-form-field {
        flex: 0 1 16rem;
      }
    }

    .limits {
      margin: 0;
    }
  `,
})
export class EvidencePanel {
  readonly evidence = input.required<readonly TicketEvidenceResponse[]>();
  readonly editable = input(false);

  private readonly facade = inject(TicketDetailFacade);
  private readonly notifier = inject(Notifier);

  protected readonly purposes: readonly EvidencePurpose[] = ['GENERAL', 'INITIAL', 'FINAL'];
  protected readonly purposeLabels = EVIDENCE_PURPOSE_LABELS;
  protected readonly accept = EVIDENCE_ACCEPT;
  protected readonly reminder = EVIDENCE_PRIVACY_REMINDER;
  protected readonly instant = formatInstant;
  protected readonly size = fileSizeText;

  protected readonly purpose = signal<EvidencePurpose>('GENERAL');
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | undefined>(undefined);

  protected asPurpose(value: string): EvidencePurpose {
    return value as EvidencePurpose;
  }

  protected url(item: TicketEvidenceResponse): string {
    return this.facade.evidenceUrl(item.id);
  }

  protected async onPicked(input: HTMLInputElement): Promise<void> {
    const file = input.files?.item(0);
    input.value = '';
    if (!file) return;
    this.errorMessage.set(undefined);
    // El servidor decide por el contenido real; esta comprobación solo evita subir un archivo que seguro se rechaza.
    if (file.size > EVIDENCE_MAX_BYTES) {
      this.errorMessage.set('El archivo supera el límite de 10 MB.');
      return;
    }
    if (file.size === 0) {
      this.errorMessage.set('El archivo está vacío.');
      return;
    }
    this.busy.set(true);
    try {
      await this.facade.uploadEvidence(file, { purpose: this.purpose() });
      this.notifier.success('Evidencia cargada');
    } catch (error) {
      this.errorMessage.set(toProblem(error).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
