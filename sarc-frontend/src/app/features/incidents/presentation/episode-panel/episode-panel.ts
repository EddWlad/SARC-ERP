import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { TicketEpisode, TicketMilestone } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { toProblem } from '../../../../core/http/problem';
import { WIDE_DIALOG } from '../../../../shared/ui/dialog-sizes';
import { TicketDetailFacade } from '../../application/ticket-detail-facade';
import {
  CAUSE_KIND_LABELS,
  CauseKind,
  CLOSURE_CHECK_KEYS,
  CLOSURE_CHECK_LABELS,
  CLOSURE_CHECK_QUESTIONS,
  CLOSURE_RESULT_LABELS,
  ClosureCheck,
  ClosureResult,
} from '../../domain/incident-closure';
import {
  DURATION_PAIR_LABELS,
  MILESTONE_KIND_LABELS,
  MilestoneKind,
  QUICK_MILESTONES,
  REFERENCE_DISCLAIMER,
  REFERENCE_MEASURE_LABELS,
  REFERENCE_RESULT_LABELS,
  ReferenceMeasure,
  ReferenceResult,
} from '../../domain/incident-labels';
import { durationText, formatInstant } from '../../domain/incident-time';
import { MilestoneDialog } from '../milestone-dialog/milestone-dialog';
import { MilestoneMark } from '../milestone-mark/milestone-mark';
import { TicketStateBadge } from '../ticket-state-badge/ticket-state-badge';

/** Código del servidor cuando el orden de los hitos pide confirmación: se pasa al diálogo para confirmarlo. */
const ORDER_UNCONFIRMED = 'MILESTONE_ORDER_UNCONFIRMED';

/**
 * Un episodio del ticket: sus cinco hitos con su marca (R, R*, N/A, S/D o —), las duraciones derivadas, el juicio frente
 * a las referencias de tiempo y, si sigue abierto, el tiempo transcurrido aparte. Los hitos T1 a T3 pendientes se
 * registran con una sola acción, «Registrar ahora» (SC-002), que guarda la hora actual y el usuario sin abrir un
 * diálogo. Elegir otra hora, «No aplica» o «Sin dato», o corregir, abre el diálogo del hito.
 */
@Component({
  selector: 'sarc-episode-panel',
  imports: [MatButtonModule, MilestoneMark, RouterLink, TicketStateBadge],
  template: `
    <section class="panel" [attr.aria-label]="'Episodio ' + episode().ordinal">
      <h2>
        Episodio {{ episode().ordinal }}
        <sarc-ticket-state-badge [state]="episode().state" />
      </h2>
      @if (episode().reopenReason) {
        <p class="muted">Reapertura: {{ episode().reopenReason }}</p>
      }

      <div class="table-wrap">
        <table class="data-table">
          <caption class="visually-hidden">
            Hitos del episodio
            {{
              episode().ordinal
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">Hito</th>
              <th scope="col">Estado</th>
              <th scope="col">Hora</th>
              <th scope="col">Motivo</th>
              @if (editable() || correctable()) {
                <th scope="col"><span class="visually-hidden">Acciones</span></th>
              }
            </tr>
          </thead>
          <tbody>
            @for (milestone of episode().milestones; track milestone.kind) {
              <tr>
                <th scope="row">
                  {{ milestone.kind }}
                  <span class="muted kind">{{ kindLabels[milestone.kind] }}</span>
                </th>
                <td>
                  <sarc-milestone-mark
                    [status]="milestone.status"
                    [retroactive]="milestone.retroactive"
                  />
                  @if (milestone.orderWarningAck) {
                    <span class="muted"> · orden confirmado</span>
                  }
                </td>
                <td>{{ instant(milestone.occurredAt) }}</td>
                <td>{{ milestone.reason ?? '—' }}</td>
                @if (editable() || correctable()) {
                  <td class="actions">
                    @if (canRegisterNow(milestone)) {
                      <button
                        matButton="filled"
                        type="button"
                        [attr.aria-label]="'Registrar ahora ' + milestone.kind"
                        (click)="registerNow(milestone)"
                      >
                        Registrar ahora
                      </button>
                    }
                    @if (canEdit(milestone)) {
                      <button
                        matButton="text"
                        type="button"
                        [attr.aria-label]="
                          (isOtherTime(milestone) ? 'Otra hora o estado de ' : 'Corregir ') +
                          milestone.kind
                        "
                        (click)="openDialog(milestone)"
                      >
                        {{ isOtherTime(milestone) ? 'Otra hora o estado' : 'Corregir' }}
                      </button>
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>

      @if (episode().closure; as closure) {
        <h3>Cierre</h3>
        <dl class="closure">
          <div>
            <dt>Resultado</dt>
            <dd>
              {{ closureResultLabels[asClosureResult(closure.result)] }}
              @if (closure.duplicateOfTicketId) {
                de
                <a [routerLink]="['../', closure.duplicateOfTicketId]">{{
                  closure.duplicateOfCode ?? closure.duplicateOfTicketId
                }}</a>
              }
            </dd>
          </div>
          <div>
            <dt>Causa</dt>
            <dd>
              {{ causeLabels[asCause(closure.causeKind)] }}
              @if (closure.causeText) {
                · {{ closure.causeText }}
              }
            </dd>
          </div>
          @if (closure.recommendations) {
            <div class="wide">
              <dt>Recomendaciones o pendientes</dt>
              <dd>{{ closure.recommendations }}</dd>
            </div>
          }
          @for (key of checkKeys; track key) {
            @if (closure.closureChecks[key]; as check) {
              <div>
                <dt>{{ checkQuestions[key] }}</dt>
                <dd>{{ checkLabels[asCheck(check)] }}</dd>
              </div>
            }
          }
          @if (closure.finalEvidenceWaiver) {
            <div class="wide">
              <dt>Sin evidencia final</dt>
              <dd>{{ closure.finalEvidenceWaiver }}</dd>
            </div>
          }
          @if (closure.closureCommunicationWaiver) {
            <div class="wide">
              <dt>Sin comunicación de cierre</dt>
              <dd>{{ closure.closureCommunicationWaiver }}</dd>
            </div>
          }
          @if (closure.deviationJustification) {
            <div class="wide">
              <dt>Justificación de la desviación</dt>
              <dd>{{ closure.deviationJustification }}</dd>
            </div>
          }
        </dl>
      }

      <h3>Tiempos</h3>
      <dl class="durations">
        @for (item of episode().durations; track item.pair) {
          <div>
            <dt>{{ pairLabels[item.pair] ?? item.pair }}</dt>
            <dd>
              {{ minutesText(item.minutes) }}
              @if (item.minutes === null || item.minutes === undefined) {
                <span class="visually-hidden"> sin dato; falta {{ missing(item.missing) }} </span>
              }
            </dd>
          </div>
        }
        @if (episode().elapsedOpenMinutes !== null && episode().elapsedOpenMinutes !== undefined) {
          <div>
            <dt>Transcurrido desde T0</dt>
            <dd>
              {{ minutesText(episode().elapsedOpenMinutes) }}
              <span class="muted">aparte, sigue abierto</span>
            </dd>
          </div>
        }
      </dl>

      @if (episode().references.length > 0) {
        <h3>Referencias de tiempo</h3>
        <ul class="references">
          @for (reference of episode().references; track reference.measure) {
            <li>
              <strong>{{ measureLabels[asMeasure(reference.measure)] }}</strong>
              · hasta {{ limitText(reference.limitMinutes) }}
              @if (reference.extendable) {
                <span class="muted">(ampliable)</span>
              }
              ·
              <span [attr.data-result]="reference.result">{{
                resultLabels[asResult(reference.result)]
              }}</span>
            </li>
          }
        </ul>
        <p class="muted disclaimer">{{ disclaimer }}</p>
      }
    </section>
  `,
  styles: `
    @use '../detail-panel';

    :host {
      display: block;
    }

    .kind {
      display: block;
      font: var(--mat-sys-body-small);
      font-weight: normal;
    }

    .closure {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
      gap: var(--sarc-space-3);
      margin: 0;

      dt {
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-label-medium);
      }

      dd {
        margin: var(--sarc-space-1) 0 0;
        overflow-wrap: anywhere;
      }

      .wide {
        grid-column: 1 / -1;
      }
    }

    .durations {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      gap: var(--sarc-space-3);
      margin: 0;

      dt {
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-label-medium);
      }

      dd {
        margin: var(--sarc-space-1) 0 0;
      }
    }

    .references {
      margin: 0;
      padding-inline-start: var(--sarc-space-4);

      [data-result='OUTSIDE'] {
        color: var(--mat-sys-error);
        font-weight: 600;
      }
    }

    .disclaimer {
      margin: var(--sarc-space-2) 0 0;
    }
  `,
})
export class EpisodePanel {
  readonly episode = input.required<TicketEpisode>();
  /** El ticket está abierto, esta es su pestaña vigente y quien mira puede editar. */
  readonly editable = input(false);
  /** El episodio está cerrado y se puede corregir con justificación (T0 a T4), por ejemplo la hora de T4. */
  readonly correctable = input(false);

  private readonly facade = inject(TicketDetailFacade);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly closureResultLabels = CLOSURE_RESULT_LABELS;
  protected readonly causeLabels = CAUSE_KIND_LABELS;
  protected readonly checkKeys = CLOSURE_CHECK_KEYS;
  protected readonly checkQuestions = CLOSURE_CHECK_QUESTIONS;
  protected readonly checkLabels = CLOSURE_CHECK_LABELS;
  protected readonly kindLabels = MILESTONE_KIND_LABELS;
  protected readonly pairLabels = DURATION_PAIR_LABELS;
  protected readonly measureLabels = REFERENCE_MEASURE_LABELS;
  protected readonly resultLabels = REFERENCE_RESULT_LABELS;
  protected readonly disclaimer = REFERENCE_DISCLAIMER;
  protected readonly instant = formatInstant;

  protected readonly ordinal = computed(() => this.episode().ordinal);

  /** El límite de una referencia: «5 min» si es menor a una hora, y «5:00 h» desde una hora. */
  protected limitText(minutes: number): string {
    return minutes < 60 ? `${minutes} min` : `${durationText(minutes)} h`;
  }

  protected minutesText(minutes: number | null | undefined): string {
    return durationText(minutes);
  }

  protected missing(kinds: readonly string[]): string {
    return kinds.join(', ');
  }

  protected asClosureResult(value: string): ClosureResult {
    return value as ClosureResult;
  }

  protected asCause(value: string): CauseKind {
    return value as CauseKind;
  }

  protected asCheck(value: string): ClosureCheck {
    return value as ClosureCheck;
  }

  protected asMeasure(value: string): ReferenceMeasure {
    return value as ReferenceMeasure;
  }

  protected asResult(value: string): ReferenceResult {
    return value as ReferenceResult;
  }

  /** T1 a T3 pendientes: una sola acción registra la hora actual. */
  protected canRegisterNow(milestone: TicketMilestone): boolean {
    return (
      this.editable() &&
      milestone.status === 'PENDING' &&
      QUICK_MILESTONES.includes(milestone.kind as MilestoneKind)
    );
  }

  /** Un hito pendiente de un episodio abierto se registra con otra hora o estado; en uno cerrado todo es corrección. */
  protected isOtherTime(milestone: TicketMilestone): boolean {
    return milestone.status === 'PENDING' && !this.correctable();
  }

  /** T4 solo se registra al cerrar; los demás hitos se pueden registrar con otra hora o corregir. */
  protected canEdit(milestone: TicketMilestone): boolean {
    return this.correctable() || milestone.kind !== 'T4';
  }

  protected async registerNow(milestone: TicketMilestone): Promise<void> {
    const kind = milestone.kind as MilestoneKind;
    try {
      await this.facade.recordMilestone(this.ordinal(), kind, { status: 'RECORDED' });
      this.notifier.success(`${kind} registrado`);
    } catch (error) {
      const problem = toProblem(error);
      if (problem.code === ORDER_UNCONFIRMED) {
        // El orden no se cumple: se explica y se confirma en el diálogo, sin guardar nada antes.
        this.notifier.error(problem.detail);
        await this.openDialog(milestone);
      } else {
        this.notifier.error(problem.detail);
      }
    }
  }

  protected async openDialog(milestone: TicketMilestone): Promise<void> {
    const kind = milestone.kind as MilestoneKind;
    const saved = await firstValueFrom(
      this.dialog
        .open(MilestoneDialog, {
          ...WIDE_DIALOG,
          data: {
            kind,
            current: milestone,
            closed: this.correctable(),
            save: (body: Parameters<TicketDetailFacade['recordMilestone']>[2]) =>
              this.facade.recordMilestone(this.ordinal(), kind, body),
          },
        })
        .afterClosed(),
    );
    if (saved === true) {
      this.notifier.success(`${kind} guardado`);
    }
  }
}
