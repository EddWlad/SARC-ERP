import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCircleUser,
  LucideDynamicIcon,
  LucideKeyRound,
  LucideLogOut,
  LucideMenu,
  LucideMonitor,
  LucideMoon,
  LucideSun,
  LucideZap,
} from '@lucide/angular';
import { map } from 'rxjs';

import { CHANGE_PASSWORD_PATH, LOGIN_PATH } from '../../core/auth/auth-guards';
import { ROLE_LABELS } from '../../core/auth/role';
import { SessionStore } from '../../core/auth/session-store';
import { ThemeMode, ThemeStore } from '../../core/theme/theme-store';
import { NAV_ITEMS } from '../navigation';

const HANDSET_QUERY = '(max-width: 1023.98px)';

@Component({
  selector: 'sarc-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    LucideDynamicIcon,
    LucideMenu,
    LucideZap,
    LucideSun,
    LucideMoon,
    LucideMonitor,
    LucideCircleUser,
    LucideKeyRound,
    LucideLogOut,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly session = inject(SessionStore);
  private readonly theme = inject(ThemeStore);
  private readonly router = inject(Router);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(HANDSET_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly menuOpen = signal(false);
  protected readonly user = this.session.user;
  protected readonly themeMode = this.theme.mode;
  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => item.roles === undefined || this.session.hasAnyRole(item.roles)),
  );
  protected readonly roleNames = computed(() =>
    (this.user()?.roles ?? []).map((role) => ROLE_LABELS[role]).join(', '),
  );
  protected readonly changePasswordPath = CHANGE_PASSWORD_PATH;
  protected readonly themeOptions: readonly { mode: ThemeMode; label: string }[] = [
    { mode: 'light', label: 'Claro' },
    { mode: 'dark', label: 'Oscuro' },
    { mode: 'system', label: 'Según el sistema' },
  ];

  protected setTheme(mode: ThemeMode): void {
    this.theme.setMode(mode);
  }

  protected closeMenuOnHandset(): void {
    if (this.isHandset()) {
      this.menuOpen.set(false);
    }
  }

  protected async logout(): Promise<void> {
    await this.session.logout();
    await this.router.navigate([LOGIN_PATH]);
  }
}
