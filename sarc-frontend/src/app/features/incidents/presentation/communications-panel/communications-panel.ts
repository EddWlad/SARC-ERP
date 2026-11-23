import { Component, input } from '@angular/core';

import { TicketCommunicationResponse } from '../../../../core/api/generated';
import {
  COMMUNICATION_CHANNEL_LABELS,
  COMMUNICATION_KIND_LABELS,
  CommunicationChannel,
  CommunicationKind,
} from '../../domain/incident-labels';
import { formatInstant } from '../../domain/incident-time';

/**
 * Comunicaciones del ticket: los avisos que el analista confirmó haber publicado a mano, con su canal, tipo y hora real.
 * Copiar el mensaje no crea ninguna; solo esta lista dice qué se envió y cuándo. Una anulada sigue visible, tachada y con
 * su motivo. Se anula desde la cronología.
 */
@Component({
  selector: 'sarc-communications-panel',
  template: `
    <section class="panel" aria-labelledby="communications-title">
      <h2 id="communications-title">Comunicaciones</h2>
      @if (communications().length === 0) {
        <p class="muted">
          Todavía no se registró ningún envío. Copiar el mensaje no lo registra: use «Registrar
          envío» después de publicarlo.
        </p>
      } @else {
        <ul class="communications">
          @for (item of communications(); track item.id) {
            <li [class.annulled]="item.annulled">
              <strong>{{ kindLabels[asKind(item.kind)] }}</strong>
              <span>
                · {{ channelLabels[asChannel(item.channel)]
                }}{{ item.channelDetail ? ': ' + item.channelDetail : '' }}
              </span>
              <span class="muted">
                · enviado {{ instant(item.sentAt) }}
                @if (item.retroactive) {
                  · retroactivo
                }
                @if (item.recordedByName) {
                  · registrado por {{ item.recordedByName }}
                }
                @if (item.annulled) {
                  · anulado: {{ item.annulReason }}
                }
              </span>
              @if (item.justification) {
                <p class="muted">Justificación: {{ item.justification }}</p>
              }
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    @use '../detail-panel';

    :host {
      display: block;
    }

    .communications {
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        padding: var(--sarc-space-2) 0;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        overflow-wrap: anywhere;
      }

      li:last-child {
        border-bottom: 0;
      }

      p {
        margin: var(--sarc-space-1) 0 0;
      }

      .annulled {
        text-decoration: line-through;
      }
    }
  `,
})
export class CommunicationsPanel {
  readonly communications = input.required<readonly TicketCommunicationResponse[]>();

  protected readonly channelLabels = COMMUNICATION_CHANNEL_LABELS;
  protected readonly kindLabels = COMMUNICATION_KIND_LABELS;
  protected readonly instant = formatInstant;

  protected asChannel(channel: string): CommunicationChannel {
    return channel as CommunicationChannel;
  }

  protected asKind(kind: string): CommunicationKind {
    return kind as CommunicationKind;
  }
}
