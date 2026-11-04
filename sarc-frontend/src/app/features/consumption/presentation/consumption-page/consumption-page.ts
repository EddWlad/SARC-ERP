import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideChartColumn, LucideFileUp, LucideHistory } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { CONSUMPTION_EDITORS } from '../../domain/consumption-permissions';

/**
 * Contenedor del modulo de consumo: secciones Tablero, Sesiones e Importaciones (solo editores).
 */
@Component({
  selector: 'sarc-consumption-page',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideChartColumn,
    LucideHistory,
    LucideFileUp,
  ],
  template: `
    <nav aria-label="Secciones de consumo" class="tabs">
      <a routerLink="tablero" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideChartColumn [size]="18" aria-hidden="true"></svg>
        Tablero
      </a>
      <a routerLink="sesiones" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideHistory [size]="18" aria-hidden="true"></svg>
        Sesiones
      </a>
      @if (canImport) {
        <a routerLink="importaciones" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideFileUp [size]="18" aria-hidden="true"></svg>
          Importaciones
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

    // En un telefono las tres secciones se reparten el ancho sin desplazamiento.
    @media (max-width: 599.98px) {
      .tabs a {
        flex: 1 1 0;
        justify-content: center;
        padding: 0 var(--sarc-space-1);

        svg {
          display: none;
        }
      }
    }
  `,
})
export class ConsumptionPage {
  protected readonly canImport = inject(SessionStore).hasAnyRole(CONSUMPTION_EDITORS);
}
