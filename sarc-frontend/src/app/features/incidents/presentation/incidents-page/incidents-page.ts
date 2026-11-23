import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideChartColumn, LucideClipboardList, LucideHash, LucideList } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { INCIDENT_NUMBERING } from '../../domain/incident-permissions';

/**
 * Contenedor del modulo de incidencias: secciones filtradas por rol, igual que Intermitencias. Los tickets los ve todo
 * usuario autenticado, igual que el tablero y el informe de control; Numeracion, solo quien puede configurarla. Registrar
 * un ticket se hace en un dialogo desde el listado, no en una seccion.
 */
@Component({
  selector: 'sarc-incidents-page',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideChartColumn,
    LucideClipboardList,
    LucideHash,
    LucideList,
  ],
  template: `
    <nav aria-label="Secciones de incidencias" class="tabs">
      <a routerLink="tickets" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideList [size]="18" aria-hidden="true"></svg>
        Tickets
      </a>
      <a routerLink="tablero" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideChartColumn [size]="18" aria-hidden="true"></svg>
        Tablero
      </a>
      <a routerLink="control" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideClipboardList [size]="18" aria-hidden="true"></svg>
        Control
      </a>
      @if (canConfigureNumbering) {
        <a routerLink="numeracion" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideHash [size]="18" aria-hidden="true"></svg>
          Numeración
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

    // Las secciones se desplazan en pantallas angostas, sin iconos para mostrar mas.
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
export class IncidentsPage {
  private readonly session = inject(SessionStore);
  protected readonly canConfigureNumbering = this.session.hasAnyRole(INCIDENT_NUMBERING);
}
