import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { QuantityFormat } from '../../format/quantity-format';
import { CONFIRM_COMMITMENT_NOTE } from '../import-batch';
import { ImportConfirmSummary, unclassifiedRows, visibleGroups } from '../import-confirm-summary';

/**
 * Confirmacion de un lote: antes de comprometerse, el analista ve cuantas filas se incorporan,
 * cuales dependen de sus decisiones y cuales no se tocan, con los conteos del propio lote. Se
 * cierra con `true` solo si confirma.
 */
@Component({
  selector: 'sarc-import-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content tabindex="-1" cdkFocusInitial>
      <p class="file">
        {{ data.fileName }} ·
        {{ quantities.amount(data.totalRows, 'fila interpretada', 'filas interpretadas') }}
      </p>

      @for (group of groups(); track group.title) {
        <section class="group">
          <h3>{{ group.title }}</h3>
          <ul [class]="'counts tone-' + group.tone">
            @for (item of group.items; track item.label) {
              <li>
                <span class="label">
                  {{ item.label }}
                  @if (item.detail) {
                    <span class="detail">{{ item.detail }}</span>
                  }
                </span>
                <strong>{{ quantities.count(item.count) }}</strong>
              </li>
            }
          </ul>
          @if (group.note) {
            <p class="note">{{ group.note }}</p>
          }
        </section>
      }

      @if (unclassified(); as rows) {
        <section class="group">
          <h3>Sin clasificar en esta pantalla</h3>
          <ul class="counts tone-untouched">
            <li>
              <span class="label">
                Otras filas
                <span class="detail">
                  el lote las cuenta en el total pero esta pantalla todavía no las nombra
                </span>
              </span>
              <strong>{{ quantities.count(rows) }}</strong>
            </li>
          </ul>
        </section>
      }

      @if (data.aside; as aside) {
        <section class="group">
          <h3>Revisión aparte</h3>
          <ul class="counts tone-conditional">
            <li>
              <span class="label">
                {{ aside.label }}
                @if (aside.detail) {
                  <span class="detail">{{ aside.detail }}</span>
                }
              </span>
              <strong>{{ quantities.count(aside.count) }}</strong>
            </li>
          </ul>
        </section>
      }

      <p class="commitment">{{ commitmentNote }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" mat-dialog-close>Cancelar</button>
      <button matButton="filled" type="button" [mat-dialog-close]="true">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .file {
      margin: 0 0 var(--sarc-space-4);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
      overflow-wrap: anywhere;
    }

    .group {
      margin-bottom: var(--sarc-space-4);

      h3 {
        margin: 0 0 var(--sarc-space-2);
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-title-small);
      }
    }

    .counts {
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--sarc-space-3);
        padding: var(--sarc-space-2) 0;
      }

      li + li {
        border-top: 1px solid var(--mat-sys-outline-variant);
      }

      strong {
        flex-shrink: 0;
        font: var(--mat-sys-title-medium);
        font-variant-numeric: tabular-nums;
      }
    }

    .label {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
      font: var(--mat-sys-body-large);
    }

    .detail {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    // El conteo dice por si solo si esas filas se aplican: marca, si depende de una decision, o
    // atenuado si el lote no las toca.
    .tone-applies strong {
      color: var(--mat-sys-primary);
    }

    .tone-conditional strong {
      color: var(--mat-sys-on-surface);
    }

    .tone-untouched {
      strong,
      .label {
        color: var(--mat-sys-on-surface-variant);
      }
    }

    .note {
      margin: var(--sarc-space-2) 0 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }

    .commitment {
      margin: 0;
      padding: var(--sarc-space-3);
      border-radius: var(--sarc-radius-sm);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ImportConfirmDialog {
  protected readonly data = inject<ImportConfirmSummary>(MAT_DIALOG_DATA);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly commitmentNote = CONFIRM_COMMITMENT_NOTE;

  protected readonly groups = computed(() => visibleGroups(this.data));
  /** 0 se trata como ausente: solo se nombra un resto cuando existe. */
  protected readonly unclassified = computed(() => unclassifiedRows(this.data) || undefined);
}
