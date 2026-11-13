import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCalendarCheck,
  LucideClipboardCheck,
  LucideClipboardPen,
  LucideFileUp,
  LucideLayoutDashboard,
  LucideList,
} from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { INTERMITTENCY_EDITORS } from '../../domain/intermittency-permissions';

/**
 * Contenedor del modulo de intermitencias: secciones filtradas por rol, igual que Uptime. Las secciones de
 * edicion solo se muestran a quien tiene algun rol de {@link INTERMITTENCY_EDITORS}; las de consulta
 * (Tablero y Ventanas) las ve todo usuario autenticado.
 */
@Component({
  selector: 'sarc-intermittency-page',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideCalendarCheck,
    LucideClipboardCheck,
    LucideClipboardPen,
    LucideFileUp,
    LucideLayoutDashboard,
    LucideList,
  ],
  template: `
    <nav aria-label="Secciones de intermitencias" class="tabs">
      <a routerLink="tablero" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideLayoutDashboard [size]="18" aria-hidden="true"></svg>
        Tablero
      </a>
      @if (canEdit) {
        <a routerLink="registrar" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideClipboardPen [size]="18" aria-hidden="true"></svg>
          Registrar
        </a>
        <a routerLink="importaciones" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideFileUp [size]="18" aria-hidden="true"></svg>
          Importaciones
        </a>
        <a routerLink="revision" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideClipboardCheck [size]="18" aria-hidden="true"></svg>
          Revisión
        </a>
        <a routerLink="dias-revisados" routerLinkActive="active" ariaCurrentWhenActive="page">
          <svg lucideCalendarCheck [size]="18" aria-hidden="true"></svg>
          Días revisados
        </a>
      }
      <a routerLink="ventanas" routerLinkActive="active" ariaCurrentWhenActive="page">
        <svg lucideList [size]="18" aria-hidden="true"></svg>
        Ventanas
      </a>
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
export class IntermittencyPage {
  protected readonly canEdit = inject(SessionStore).hasAnyRole(INTERMITTENCY_EDITORS);
}
