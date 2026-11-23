import { Component, input } from '@angular/core';

import { TICKET_STATE_LABELS, TicketState } from '../../domain/incident-labels';

/**
 * Estado principal de un ticket: Abierto o Cerrado (FR-030). El estado va siempre con texto, no solo con color
 * (FR-064). Un ticket abierto se destaca; uno cerrado, no.
 */
@Component({
  selector: 'sarc-ticket-state-badge',
  template: `
    <span
      class="badge"
      [class.badge-off]="state() === 'CLOSED'"
      [class.badge-warn]="state() === 'OPEN'"
      >{{ labels[state()] }}</span
    >
  `,
  styles: `
    @use '../../../../shared/ui/badges';
  `,
})
export class TicketStateBadge {
  readonly state = input.required<TicketState>();

  protected readonly labels = TICKET_STATE_LABELS;
}
