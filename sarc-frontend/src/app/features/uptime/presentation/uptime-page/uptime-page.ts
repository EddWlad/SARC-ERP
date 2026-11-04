import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideChartColumn,
  LucideClipboardCheck,
  LucideClipboardPen,
  LucideFileUp,
  LucideLink,
} from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { UPTIME_EDITORS } from '../../domain/uptime-permissions';

/** Contenedor del modulo de uptime: secciones filtradas por rol, igual que Consumo. */
@Component({
  selector: 'sarc-uptime-page',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideChartColumn,
    LucideClipboardCheck,
    LucideClipboardPen,
    LucideFileUp,
    LucideLink,
  ],
  template: `
    <nav aria-label="Secciones de uptime" class="tabs">
      <a routerLink="tablero" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideChartColumn [size]="18" aria-hidden="true"></svg>
        Tablero
      </a>
      @if (canEdit) {
        <a routerLink="captura" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideClipboardPen [size]="18" aria-hidden="true"></svg>
          Captura
        </a>
        <a routerLink="revision" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideClipboardCheck [size]="18" aria-hidden="true"></svg>
          Revisión
        </a>
        <a routerLink="importaciones" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideFileUp [size]="18" aria-hidden="true"></svg>
          Importaciones
        </a>
        <a routerLink="vinculacion" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideLink [size]="18" aria-hidden="true"></svg>
          Vinculación
        </a>
      }
    </nav>
    <router-outlet />
  `,
  styles: `
    @use '../../../../shared/ui/section-tabs';

    :host {
      display: block;
    }

    // Cinco secciones no caben a 360 px: se desplazan, sin iconos para mostrar mas.
    @media (max-width: 599.98px) {
      .tabs a {
        padding: 0 var(--sarc-space-3);

        svg {
          display: none;
        }
      }
    }
  `,
})
export class UptimePage {
  protected readonly canEdit = inject(SessionStore).hasAnyRole(UPTIME_EDITORS);
}
