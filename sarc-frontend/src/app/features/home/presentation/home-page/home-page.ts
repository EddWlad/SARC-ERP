import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideDynamicIcon } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { NAV_ITEMS } from '../../../../layout/navigation';

/** Pagina de inicio: saludo y accesos directos a los modulos que el usuario puede ver. */
@Component({
  selector: 'sarc-home-page',
  imports: [RouterLink, LucideDynamicIcon],
  template: `
    <h1>Hola, {{ firstName() }}</h1>
    <p class="lead">Seleccione un módulo para comenzar.</p>

    <ul class="shortcuts">
      @for (item of shortcuts(); track item.path) {
        <li>
          <a class="shortcut" [routerLink]="item.path">
            <svg [lucideIcon]="item.icon" [size]="28"></svg>
            <span>{{ item.label }}</span>
          </a>
        </li>
      }
    </ul>
  `,
  styles: `
    h1 {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }

    .lead {
      margin: var(--sarc-space-1) 0 var(--sarc-space-5);
      color: var(--mat-sys-on-surface-variant);
    }

    .shortcuts {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: var(--sarc-space-4);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .shortcut {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      padding: var(--sarc-space-5);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-md);
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-title-medium);
      text-decoration: none;
      transition: background-color var(--sarc-motion-fast) var(--sarc-easing);

      svg {
        color: var(--mat-sys-primary);
      }

      &:hover {
        background: var(--mat-sys-surface-container);
      }
    }
  `,
})
export class HomePage {
  private readonly session = inject(SessionStore);

  protected readonly firstName = computed(() => this.session.user()?.fullName.split(' ')[0] ?? '');
  protected readonly shortcuts = computed(() =>
    NAV_ITEMS.filter(
      (item) =>
        item.path !== '/inicio' &&
        (item.roles === undefined || this.session.hasAnyRole(item.roles)),
    ),
  );
}
