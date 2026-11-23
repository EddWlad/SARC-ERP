import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

import { TicketEventResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { TicketDetailFacade } from '../../application/ticket-detail-facade';
import {
  EVENT_TYPE_LABELS,
  EventType,
  isAnnullableEvent,
  TARGET_KIND_LABELS,
  TargetKind,
} from '../../domain/incident-labels';
import { formatInstant } from '../../domain/incident-time';
import { eventSummary } from '../../domain/incident-timeline';
import { AnnulDialog } from '../annul-dialog/annul-dialog';

/**
 * Cronología del ticket: todos los eventos por hora del hecho, con su autor, la marca de retroactivo y, si se anularon,
 * el motivo. Nada se borra: un evento manual o una evidencia cargada por error se anula con motivo y sigue visible.
 */
@Component({
  selector: 'sarc-timeline-panel',
  imports: [MatButtonModule],
  template: `
    <section class="panel" aria-labelledby="timeline-title">
      <h2 id="timeline-title">Cronología</h2>
      <ol class="timeline">
        @for (event of timeline(); track event.id) {
          <li [class.annulled]="event.annulled">
            <div class="head">
              <strong>{{ typeLabels[asType(event.type)] }}</strong>
              @if (event.targetKind) {
                <span>· a {{ targetLabels[asTarget(event.targetKind)] }}</span>
              }
              <span class="muted">
                · {{ instant(event.occurredAt) }}
                @if (event.recordedByName) {
                  · {{ event.recordedByName }}
                }
                @if (event.retroactive) {
                  · retroactivo
                }
                @if (event.annulled) {
                  · anulado: {{ event.annulReason }}
                }
              </span>
              @if (editable() && canAnnul(event)) {
                <button
                  matButton="text"
                  type="button"
                  [attr.aria-label]="
                    'Anular ' + typeLabels[asType(event.type)] + ' del ' + instant(event.occurredAt)
                  "
                  (click)="annul(event)"
                >
                  Anular
                </button>
              }
            </div>
            @for (line of summary(event); track $index) {
              <p class="summary">{{ line }}</p>
            }
            @if (event.text) {
              <p>{{ event.text }}</p>
            }
          </li>
        }
      </ol>
    </section>
  `,
  styles: `
    @use '../detail-panel';

    :host {
      display: block;
    }

    .timeline {
      margin: 0;
      padding-inline-start: var(--sarc-space-4);

      li {
        margin-bottom: var(--sarc-space-3);
      }

      p {
        margin: var(--sarc-space-1) 0 0;
        overflow-wrap: anywhere;
      }

      .summary {
        color: var(--mat-sys-on-surface-variant);
      }

      .annulled {
        text-decoration: line-through;
      }
    }

    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-1) var(--sarc-space-2);
    }
  `,
})
export class TimelinePanel {
  readonly timeline = input.required<readonly TicketEventResponse[]>();
  readonly editable = input(false);

  private readonly facade = inject(TicketDetailFacade);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly typeLabels = EVENT_TYPE_LABELS;
  protected readonly targetLabels = TARGET_KIND_LABELS;
  protected readonly instant = formatInstant;

  protected asType(type: string): EventType {
    return type as EventType;
  }

  protected asTarget(kind: string): TargetKind {
    return kind as TargetKind;
  }

  protected summary(event: TicketEventResponse): string[] {
    return eventSummary(this.asType(event.type), event.details, event.targetRoleOrProvider);
  }

  protected canAnnul(event: TicketEventResponse): boolean {
    return !event.annulled && isAnnullableEvent(this.asType(event.type));
  }

  protected async annul(event: TicketEventResponse): Promise<void> {
    const isEvidence = event.type === 'EVIDENCE_ADDED';
    const annulled = await firstValueFrom(
      this.dialog
        .open(AnnulDialog, {
          data: {
            title: isEvidence ? 'Anular evidencia' : 'Anular evento',
            message:
              'Lo anulado no se borra: sigue visible en la cronología con su motivo y en la auditoría.',
            save: (reason: string) => this.facade.annulEvent(event.id, { reason }),
          },
        })
        .afterClosed(),
    );
    if (annulled === true) {
      this.notifier.success(isEvidence ? 'Evidencia anulada' : 'Evento anulado');
    }
  }
}
