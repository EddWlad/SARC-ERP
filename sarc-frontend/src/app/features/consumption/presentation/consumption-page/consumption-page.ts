import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideChartColumn, LucideFileUp, LucideHistory } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { CONSUMPTION_EDITORS } from '../../domain/consumption-permissions';

/**
 * Contenedor del modulo de consumo: secciones Tablero, Sesiones e Importaciones (solo editores).
 * Son enlaces de navegacion con aspecto de pestañas: mat-tab-nav-bar sumaba al bundle inicial
 * dependencias del CDK que tres enlaces no necesitan.
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
    :host {
      display: block;
    }

    .tabs {
      display: flex;
      overflow-x: auto;
      margin-bottom: var(--sarc-space-5);
      border-bottom: 1px solid var(--mat-sys-outline-variant);

      a {
        display: inline-flex;
        flex-shrink: 0;
        align-items: center;
        gap: var(--sarc-space-2);
        min-height: 48px;
        padding: 0 var(--sarc-space-4);
        border-bottom: 3px solid transparent;
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-title-small);
        text-decoration: none;

        &:hover {
          background: color-mix(in srgb, var(--mat-sys-on-surface) 8%, transparent);
          color: var(--mat-sys-on-surface);
        }

        &:focus-visible {
          outline: 2px solid var(--mat-sys-primary);
          outline-offset: -2px;
        }

        &.active {
          border-bottom-color: var(--mat-sys-primary);
          color: var(--mat-sys-primary);
        }

        // En un telefono las tres secciones se reparten el ancho sin desplazamiento.
        @media (max-width: 599.98px) {
          flex: 1 1 0;
          justify-content: center;
          padding: 0 var(--sarc-space-1);

          svg {
            display: none;
          }
        }
      }
    }
  `,
})
export class ConsumptionPage {
  protected readonly canImport = inject(SessionStore).hasAnyRole(CONSUMPTION_EDITORS);
}
