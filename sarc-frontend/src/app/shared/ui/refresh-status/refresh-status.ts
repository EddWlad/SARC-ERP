import { Component, computed, input } from '@angular/core';
import { LucideWifiOff } from '@lucide/angular';

import { RefreshStatus } from '../../../core/http/auto-refresh';

/** Zona operativa: America/Guayaquil. */
const TIME_ZONE = 'America/Guayaquil';

/**
 * Hora de la ultima actualizacion y aviso de perdida de conectividad (FR-063). El aviso va con texto y un icono, no solo
 * con color, y se anuncia a los lectores de pantalla; la hora se muestra siempre que haya una.
 */
@Component({
  selector: 'sarc-refresh-status',
  imports: [LucideWifiOff],
  template: `
    <p class="status" role="status" aria-live="polite">
      @if (updatedText(); as text) {
        <span class="time">Actualizado a las {{ text }}</span>
      }
      @if (status().connectionLost()) {
        <span class="lost">
          <svg lucideWifiOff [size]="16" aria-hidden="true"></svg>
          Sin conexión con el servidor: se muestran los últimos datos.
        </span>
      }
    </p>
  `,
  styles: `
    :host {
      display: block;
    }

    .status {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-1) var(--sarc-space-3);
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-medium);
    }

    .lost {
      display: inline-flex;
      align-items: center;
      gap: var(--sarc-space-1);
      color: var(--mat-sys-error);
      font-weight: 500;
    }
  `,
})
export class RefreshStatusIndicator {
  readonly status = input.required<RefreshStatus>();

  protected readonly updatedText = computed(() => {
    const at = this.status().lastUpdatedAt();
    return at === null ? '' : formatClock(at);
  });
}

/** HH:mm en la zona operativa. */
export function formatClock(at: Date): string {
  return new Intl.DateTimeFormat('es-EC', {
    timeZone: TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(at);
}
