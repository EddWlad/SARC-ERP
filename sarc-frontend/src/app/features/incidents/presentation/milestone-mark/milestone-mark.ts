import { Component, computed, input } from '@angular/core';

import {
  MILESTONE_STATUS_LABELS,
  MilestoneStatus,
  milestoneMark,
} from '../../domain/incident-labels';

/**
 * Marca de un hito (FR-042): R registrado, R* retroactivo, N/A no aplica, S/D sin dato y — pendiente. La marca es
 * corta y visual; el estado completo va como texto para los lectores de pantalla, de modo que nunca se depende solo
 * del color ni de la abreviatura.
 */
@Component({
  selector: 'sarc-milestone-mark',
  template: `
    <span
      class="mark"
      [attr.data-status]="status()"
      [class.retro]="retroactive()"
      aria-hidden="true"
      >{{ mark() }}</span
    >
    <span class="visually-hidden">{{ text() }}</span>
  `,
  styles: `
    :host {
      display: inline-block;
    }

    .mark {
      display: inline-block;
      min-width: 3ch;
      padding: 0 var(--sarc-space-2);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-sm);
      font: var(--mat-sys-label-large);
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }

    .mark[data-status='RECORDED'] {
      border-color: transparent;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .mark[data-status='RECORDED'].retro {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
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
export class MilestoneMark {
  readonly status = input.required<MilestoneStatus>();
  readonly retroactive = input(false);

  protected readonly mark = computed(() => milestoneMark(this.status(), this.retroactive()));
  protected readonly text = computed(() =>
    this.status() === 'RECORDED' && this.retroactive()
      ? 'Registrado con hora retroactiva'
      : MILESTONE_STATUS_LABELS[this.status()],
  );
}
