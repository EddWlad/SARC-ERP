import { Component, input } from '@angular/core';

import { SESSION_STATE_HINTS, SESSION_STATE_LABELS, SessionState } from '../../domain/consumption';

/** Estado de calidad de una sesion: validada, pendiente de vincular o prueba. */
@Component({
  selector: 'sarc-session-state-badge',
  template: `
    <span
      class="badge"
      [class.badge-warn]="state() === 'PENDING_LINK'"
      [class.badge-accent]="state() === 'AWAITING_APPROVAL'"
      [class.badge-off]="state() === 'TEST'"
      [title]="hints[state()]"
      >{{ labels[state()] }}</span
    >
  `,
  styles: `
    @use '../../../../shared/ui/badges';
  `,
})
export class SessionStateBadge {
  readonly state = input.required<SessionState>();

  protected readonly labels = SESSION_STATE_LABELS;
  protected readonly hints = SESSION_STATE_HINTS;
}
