import { Component, computed, input } from '@angular/core';

import { User, USER_STATUS_LABELS } from '../../domain/user';

/** Estado del usuario: activo/inactivo, clave temporal y bloqueo por intentos fallidos. */
@Component({
  selector: 'sarc-user-badges',
  template: `
    <span class="badge" [class.badge-off]="user().status === 'INACTIVE'">{{ statusLabel() }}</span>
    @if (user().mustChangePassword) {
      <span class="badge badge-info">Clave temporal</span>
    }
    @if (locked()) {
      <span class="badge badge-warn">Bloqueado</span>
    }
  `,
  styles: `
    @use '../../../../shared/ui/badges';

    :host {
      display: inline-flex;
      flex-wrap: wrap;
      gap: var(--sarc-space-1);
    }
  `,
})
export class UserBadges {
  readonly user = input.required<User>();

  protected readonly statusLabel = computed(() => USER_STATUS_LABELS[this.user().status]);
  /** El bloqueo vence solo: el backend conserva la fecha hasta el siguiente ingreso. */
  protected readonly locked = computed(() => {
    const lockedUntil = this.user().lockedUntil;
    return lockedUntil != null && Date.parse(lockedUntil) > Date.now();
  });
}
