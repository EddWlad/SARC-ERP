import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

import { TicketVisitResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { WIDE_DIALOG } from '../../../../shared/ui/dialog-sizes';
import { TicketDetailFacade } from '../../application/ticket-detail-facade';
import { VISIT_NEXT_STAGES, VISIT_STAGE_LABELS, VisitStage } from '../../domain/incident-labels';
import { formatInstant } from '../../domain/incident-time';
import { VisitDialog, VisitDialogResult } from '../visit-dialog/visit-dialog';

/**
 * Visitas técnicas del ticket, registradas a mano (P-08): proveedor, etapa actual y, si se canceló o aplazó, quién lo
 * decidió, con qué evidencia y bajo qué condición se vuelve a escalar. SARC no asigna ni avisa al proveedor.
 */
@Component({
  selector: 'sarc-visits-panel',
  imports: [MatButtonModule],
  template: `
    <section class="panel" aria-labelledby="visits-title">
      <div class="panel-heading">
        <h2 id="visits-title">Visitas técnicas</h2>
        @if (editable()) {
          <button matButton="tonal" type="button" (click)="request()">Solicitar visita</button>
        }
      </div>
      @if (visits().length === 0) {
        <p class="muted">Este ticket no tiene visitas técnicas registradas.</p>
      } @else {
        <ul class="visits">
          @for (visit of visits(); track visit.id) {
            <li>
              <div class="line">
                <strong>{{ stageLabels[asStage(visit.stage)] }}</strong>
                <span class="muted">
                  · {{ visit.providerName ?? 'Proveedor sin indicar' }} · episodio
                  {{ visit.episodeOrdinal }} · actualizada {{ instant(visit.updatedAt) }}
                </span>
                @if (editable() && hasNext(visit)) {
                  <button
                    matButton="text"
                    type="button"
                    [attr.aria-label]="
                      'Cambiar la etapa de la visita de ' +
                      (visit.providerName ?? 'proveedor sin indicar')
                    "
                    (click)="move(visit)"
                  >
                    Cambiar etapa
                  </button>
                }
              </div>
              @if (
                visit.cancelDecidedBy &&
                (visit.stage === 'CANCELLED' || visit.stage === 'POSTPONED')
              ) {
                <p class="decision">
                  Decidió: {{ visit.cancelDecidedBy }}. Evidencia: {{ visit.cancelEvidenceNote }}.
                  Se vuelve a escalar si: {{ visit.reescalationCondition }}.
                </p>
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

    .visits {
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        padding: var(--sarc-space-2) 0;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
      }

      li:last-child {
        border-bottom: 0;
      }
    }

    .line {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-1) var(--sarc-space-2);
    }

    .decision {
      margin: var(--sarc-space-1) 0 0;
      overflow-wrap: anywhere;
    }
  `,
})
export class VisitsPanel {
  readonly visits = input.required<readonly TicketVisitResponse[]>();
  readonly editable = input(false);

  private readonly facade = inject(TicketDetailFacade);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly stageLabels = VISIT_STAGE_LABELS;
  protected readonly instant = formatInstant;

  protected asStage(stage: string): VisitStage {
    return stage as VisitStage;
  }

  protected hasNext(visit: TicketVisitResponse): boolean {
    return VISIT_NEXT_STAGES[visit.stage].length > 0;
  }

  protected async request(): Promise<void> {
    await this.open(undefined, (result) => this.facade.requestVisit(result), 'Visita solicitada');
  }

  protected async move(visit: TicketVisitResponse): Promise<void> {
    await this.open(
      visit,
      (result) => {
        const { stage, ...rest } = result;
        if (stage === undefined) {
          throw new Error('Falta la etapa');
        }
        return this.facade.moveVisitStage(visit.id, { stage, ...rest });
      },
      'Etapa de la visita actualizada',
    );
  }

  private async open(
    visit: TicketVisitResponse | undefined,
    save: (result: VisitDialogResult) => Promise<void>,
    success: string,
  ): Promise<void> {
    const saved = await firstValueFrom(
      this.dialog
        .open(VisitDialog, {
          ...WIDE_DIALOG,
          data: { visit, providers: (search: string) => this.facade.providers(search), save },
        })
        .afterClosed(),
    );
    if (saved === true) {
      this.notifier.success(success);
    }
  }
}
