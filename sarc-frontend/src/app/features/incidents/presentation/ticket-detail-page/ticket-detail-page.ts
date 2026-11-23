import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideChevronDown, LucideMapPin } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { Notifier } from '../../../../core/notifications/notifier';
import { WIDE_DIALOG } from '../../../../shared/ui/dialog-sizes';
import { RefreshStatusIndicator } from '../../../../shared/ui/refresh-status/refresh-status';
import { TicketDetailFacade } from '../../application/ticket-detail-facade';
import {
  ELEMENT_CLASS_LABELS,
  networkLabel,
  PRIORITY_LABELS,
  RELATION_KIND_LABELS,
  REPORT_ORIGIN_LABELS,
  ReportOrigin,
  suggestedCommunicationKind,
  TICKET_CONDITION_LABELS,
} from '../../domain/incident-labels';
import { ageText } from '../../domain/incident-time';
import { CloseDialog } from '../close-dialog/close-dialog';
import { CorrectionDialog } from '../correction-dialog/correction-dialog';
import { EpisodePanel } from '../episode-panel/episode-panel';
import { EventDialog } from '../event-dialog/event-dialog';
import { EvidencePanel } from '../evidence-panel/evidence-panel';
import { HandoverDialog, HandoverMode } from '../handover-dialog/handover-dialog';
import { CommunicationDialog } from '../communication-dialog/communication-dialog';
import { CommunicationsPanel } from '../communications-panel/communications-panel';
import { MessageDialog } from '../message-dialog/message-dialog';
import { PriorityDialog } from '../priority-dialog/priority-dialog';
import { ReclassifyDialog } from '../reclassify-dialog/reclassify-dialog';
import { ReopenDialog } from '../reopen-dialog/reopen-dialog';
import { TicketStateBadge } from '../ticket-state-badge/ticket-state-badge';
import { TimelinePanel } from '../timeline-panel/timeline-panel';
import { VisitsPanel } from '../visits-panel/visits-panel';

/**
 * Detalle de un ticket: el codigo y el estado, el elemento con su nombre actual y su etiqueta en T0, la descripcion, el
 * origen y la prioridad, y por cada episodio sus hitos, duraciones y referencias de tiempo. Los editores registran el
 * seguimiento desde aqui (hitos, eventos, visitas, relevo, evidencias y correcciones); quien solo consulta ve lo mismo
 * sin acciones. El cierre y la reapertura llegan en las historias siguientes.
 */
@Component({
  selector: 'sarc-ticket-detail-page',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatMenuModule,
    MatProgressBarModule,
    RouterLink,
    LucideArrowLeft,
    LucideChevronDown,
    LucideMapPin,
    CommunicationsPanel,
    EpisodePanel,
    EvidencePanel,
    RefreshStatusIndicator,
    TicketStateBadge,
    TimelinePanel,
    VisitsPanel,
  ],
  providers: [TicketDetailFacade],
  template: `
    @if (facade.loading() && !facade.detail()) {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando el ticket" />
    }

    @if (facade.failed()) {
      <div class="state" role="alert">
        <p>{{ facade.notFound() ? 'El ticket no existe.' : facade.errorMessage() }}</p>
        <a matButton="tonal" routerLink="../../tickets">Volver a los tickets</a>
      </div>
    }

    @if (facade.detail(); as detail) {
      <header class="page-header">
        <div>
          <a class="back" routerLink="../../tickets">
            <svg lucideArrowLeft [size]="16" aria-hidden="true"></svg>
            Tickets
          </a>
          <h1>
            {{ detail.summary.code }}
            <sarc-ticket-state-badge [state]="detail.summary.state" />
          </h1>
          <p>{{ detail.summary.elementLabel }} · {{ detail.summary.incidentTypeLabel }}</p>
          <sarc-refresh-status [status]="facade.refresh" />
        </div>
        <div class="header-actions">
          @if (can('EVENT')) {
            <button matButton="filled" type="button" (click)="addEvent()">Registrar evento</button>
          }
          @if (can('REOPEN')) {
            <button matButton="filled" type="button" (click)="reopen()">Reabrir ticket</button>
          }
          @if (can('CLOSE')) {
            <button matButton="tonal" type="button" (click)="closeTicket()">Cerrar ticket</button>
          }
          @if (can('HANDOVER')) {
            <button matButton="tonal" type="button" (click)="handover('handover')">
              Relevo de turno
            </button>
          }
          <button matButton="tonal" type="button" (click)="openMessage()">
            Mensaje para WhatsApp
          </button>
          @if (can('RESPONSIBLE') || can('CORRECT') || can('PRIORITY') || can('RECLASSIFY')) {
            <button matButton="outlined" type="button" [matMenuTriggerFor]="moreMenu">
              Más acciones
              <svg lucideChevronDown [size]="18" aria-hidden="true"></svg>
            </button>
            <mat-menu #moreMenu="matMenu" xPosition="before">
              @if (can('RESPONSIBLE')) {
                <button mat-menu-item type="button" (click)="handover('assign')">
                  Asignar responsable
                </button>
              }
              @if (can('PRIORITY')) {
                <button mat-menu-item type="button" (click)="changePriority()">
                  Cambiar prioridad
                </button>
              }
              @if (can('RECLASSIFY')) {
                <button mat-menu-item type="button" (click)="reclassify()">Reclasificar</button>
              }
              @if (can('CORRECT')) {
                <button mat-menu-item type="button" (click)="correct()">Corregir datos</button>
              }
            </mat-menu>
          }
        </div>
      </header>

      @if (facade.conflict()) {
        <div class="refresh-alert" role="alert">
          <p>Otro usuario modificó este ticket. Recargue para ver los cambios y continuar.</p>
          <button matButton type="button" (click)="facade.reload()">Recargar</button>
        </div>
      }

      @if (facade.refreshFailed()) {
        <div class="refresh-alert" role="status">
          <p>No se pudo actualizar el ticket. Se muestran los datos anteriores.</p>
          <button matButton type="button" (click)="facade.reload()">Reintentar</button>
        </div>
      }

      <div class="grid">
        <section class="panel" aria-labelledby="element-title">
          <h2 id="element-title">Elemento afectado</h2>
          <dl>
            <div>
              <dt>Clase</dt>
              <dd>{{ classes[detail.summary.elementClass] }}</dd>
            </div>
            <div>
              <dt>Elemento</dt>
              <dd>{{ detail.summary.elementLabel }}</dd>
            </div>
            @if (detail.equipment; as equipment) {
              @if (equipment.labelAtT0 !== equipment.currentName) {
                <div>
                  <dt>Nombre en T0</dt>
                  <dd>{{ equipment.labelAtT0 }}</dd>
                </div>
              }
              @if (!equipment.active) {
                <div>
                  <dt>Equipo</dt>
                  <dd>Ya no está activo en el inventario</dd>
                </div>
              }
            }
            <div>
              <dt>Red en T0</dt>
              <dd>{{ network(detail.summary.network) }}</dd>
            </div>
            @if (detail.connectors.length > 0) {
              <div>
                <dt>Conectores</dt>
                <dd>
                  @for (connector of detail.connectors; track connector.id) {
                    <span class="connector">
                      Conector {{ connector.currentPosition ?? connector.positionAtT0 }}
                      @if (
                        connector.currentPosition &&
                        connector.currentPosition !== connector.positionAtT0
                      ) {
                        <span class="muted">(en T0: {{ connector.positionAtT0 }})</span>
                      }
                    </span>
                  }
                </dd>
              </div>
            } @else if (detail.equipment) {
              <div>
                <dt>Afectación</dt>
                <dd>Equipo completo</dd>
              </div>
            }
            @if (detail.siteElement; as site) {
              <div>
                <dt>Elemento del sitio</dt>
                <dd>{{ site.kindLabel }}{{ site.elementName ? ' · ' + site.elementName : '' }}</dd>
              </div>
            }
            @if (detail.chatbotScope) {
              <div>
                <dt>Alcance</dt>
                <dd>{{ detail.chatbotScope === 'TOTAL' ? 'Total' : 'Parcial' }}</dd>
              </div>
            }
            @if (detail.location || detail.mapsUrl) {
              <div class="wide">
                <dt>Ubicación</dt>
                <dd>
                  {{ detail.location ?? 'Sin ubicación' }}
                  @if (detail.mapsUrl) {
                    <a
                      class="maps"
                      [href]="detail.mapsUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <svg lucideMapPin [size]="16" aria-hidden="true"></svg>
                      Ver en Google Maps
                    </a>
                  }
                </dd>
              </div>
            }
          </dl>
        </section>

        <section class="panel" aria-labelledby="case-title">
          <h2 id="case-title">Caso</h2>
          <dl>
            <div class="wide">
              <dt>Descripción</dt>
              <dd>{{ detail.description }}</dd>
            </div>
            <div>
              <dt>Origen del aviso</dt>
              <dd>
                {{ origins[detail.reportOrigin]
                }}{{ detail.reportOriginDetail ? ': ' + detail.reportOriginDetail : '' }}
              </dd>
            </div>
            <div>
              <dt>Prioridad</dt>
              <dd>{{ priorities[detail.summary.priority] }}</dd>
            </div>
            <div>
              <dt>Responsable</dt>
              <dd>{{ detail.summary.responsible.name }}</dd>
            </div>
            @if (detail.summary.condition; as condition) {
              <div>
                <dt>Condición</dt>
                <dd>{{ conditions[condition] }}</dd>
              </div>
            }
            @if (detail.summary.state === 'OPEN') {
              <div>
                <dt>Antigüedad</dt>
                <dd>{{ age(detail.summary.t0) }}</dd>
              </div>
            }
            @if (detail.referenceVersion; as version) {
              <div class="wide">
                <dt>Referencias de tiempo</dt>
                <dd>
                  {{ version.label }}
                  <span class="muted">· referencia operativa, no SLA contractual</span>
                </dd>
              </div>
            }
          </dl>
        </section>
      </div>

      @if (detail.episodes.length > 1) {
        <div class="episodes" role="group" aria-labelledby="episodes-label">
          <span id="episodes-label" class="episodes-label">Episodios</span>
          <mat-button-toggle-group
            hideSingleSelectionIndicator
            aria-labelledby="episodes-label"
            [value]="shownOrdinal()"
            (change)="selected.set($event.value)"
          >
            @for (item of detail.episodes; track item.ordinal) {
              <mat-button-toggle [value]="item.ordinal">
                Episodio {{ item.ordinal }} · {{ item.state === 'OPEN' ? 'abierto' : 'cerrado' }}
              </mat-button-toggle>
            }
          </mat-button-toggle-group>
          @if (!isLatestShown()) {
            <p class="muted">Episodio anterior: solo lectura.</p>
          }
        </div>
      }
      @if (shownEpisode(); as episode) {
        <sarc-episode-panel
          [episode]="episode"
          [editable]="can('MILESTONE') && isLatestShown()"
          [correctable]="can('CORRECT_MILESTONES') && isLatestShown() && episode.state === 'CLOSED'"
        />
      }

      <sarc-visits-panel [visits]="detail.visits" [editable]="can('VISIT')" />

      <sarc-evidence-panel [evidence]="detail.evidence" [editable]="can('EVIDENCE')" />

      <sarc-communications-panel [communications]="detail.communications" />

      <section class="panel" aria-labelledby="relations-title">
        <h2 id="relations-title">Tickets relacionados</h2>
        @if (detail.relations.length === 0) {
          <p class="muted">Este ticket no está relacionado con otros.</p>
        } @else {
          <ul>
            @for (relation of detail.relations; track relation.ticketId) {
              <li>
                <a [routerLink]="['../', relation.ticketId]">{{
                  relation.code ?? relation.ticketId
                }}</a>
                · {{ relationKinds[relation.kind] }}
                @if (relation.reason) {
                  <span class="muted">· {{ relation.reason }}</span>
                }
              </li>
            }
          </ul>
        }
      </section>

      <sarc-timeline-panel [timeline]="detail.timeline" [editable]="can('EVENT')" />
    }
  `,
  styles: `
    @use '../../../../shared/ui/page-chrome';
    @use '../detail-panel';

    :host {
      display: block;
    }

    .back {
      display: inline-flex;
      align-items: center;
      gap: var(--sarc-space-1);
      margin-bottom: var(--sarc-space-2);
      color: var(--mat-sys-primary);
    }

    h1 {
      display: flex;
      align-items: center;
      gap: var(--sarc-space-3);
    }

    .episodes {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      margin-bottom: var(--sarc-space-3);

      p {
        margin: 0;
      }
    }

    .episodes-label {
      font: var(--mat-sys-label-large);
    }

    mat-button-toggle-group {
      --mat-button-toggle-shape: var(--mat-sys-corner-medium);
      width: 100%;
      flex-wrap: wrap;
    }

    mat-button-toggle {
      flex: 1 1 auto;
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
      gap: var(--sarc-space-4);
      margin-bottom: var(--sarc-space-4);
    }

    .grid .panel {
      margin-bottom: 0;
    }

    dl {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
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

    .connector {
      margin-inline-end: var(--sarc-space-3);
    }

    .maps {
      display: inline-flex;
      align-items: center;
      gap: var(--sarc-space-1);
      margin-inline-start: var(--sarc-space-2);
      color: var(--mat-sys-primary);
    }

    .refresh-alert {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-3);
      padding: var(--sarc-space-2) var(--sarc-space-3);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);

      p {
        margin: 0;
      }

      button {
        --mat-button-text-label-text-color: var(--mat-sys-on-error-container);
        --mat-button-text-state-layer-color: var(--mat-sys-on-error-container);
        border: 1px solid currentColor;
      }
    }
  `,
})
export class TicketDetailPage {
  /** Id del ticket, de la ruta `tickets/:id`. */
  readonly id = input.required<string>();

  protected readonly facade = inject(TicketDetailFacade);

  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  /** El episodio que elige quien mira; vacío es el actual (el último). */
  protected readonly selected = signal<number | null>(null);
  protected readonly shownOrdinal = computed(() => {
    const episodes = this.facade.detail()?.episodes ?? [];
    const chosen = this.selected();
    return episodes.some((episode) => episode.ordinal === chosen)
      ? chosen
      : (episodes.at(-1)?.ordinal ?? 1);
  });
  protected readonly shownEpisode = computed(() =>
    this.facade.detail()?.episodes.find((episode) => episode.ordinal === this.shownOrdinal()),
  );
  /** Solo el último episodio se edita; los anteriores se ven en solo lectura. */
  protected readonly isLatestShown = computed(() => {
    const episodes = this.facade.detail()?.episodes ?? [];
    return episodes.at(-1)?.ordinal === this.shownOrdinal();
  });

  protected readonly classes = ELEMENT_CLASS_LABELS;
  protected readonly origins = REPORT_ORIGIN_LABELS;
  protected readonly priorities = PRIORITY_LABELS;
  protected readonly conditions = TICKET_CONDITION_LABELS;
  protected readonly relationKinds = RELATION_KIND_LABELS;
  protected readonly network = networkLabel;
  protected readonly age = ageText;

  /** Acciones que el servidor permite a quien mira, según su rol y el estado del ticket (solo para mostrar botones). */
  private readonly actions = computed(() => new Set(this.facade.detail()?.allowedActions ?? []));

  constructor() {
    effect(() => void this.facade.open(this.id()));
  }

  protected can(action: string): boolean {
    return this.actions().has(action);
  }

  protected async addEvent(): Promise<void> {
    const saved = await firstValueFrom(
      this.dialog
        .open(EventDialog, {
          ...WIDE_DIALOG,
          data: {
            save: (body: Parameters<TicketDetailFacade['addEvent']>[0]) =>
              this.facade.addEvent(body),
          },
        })
        .afterClosed(),
    );
    if (saved === true) this.notifier.success('Evento registrado');
  }

  protected async handover(mode: HandoverMode): Promise<void> {
    const detail = this.facade.detail();
    if (detail === undefined) return;
    const saved = await firstValueFrom(
      this.dialog
        .open(HandoverDialog, {
          ...WIDE_DIALOG,
          data: {
            mode,
            currentResponsibleId: detail.summary.responsible.id,
            responsibles: () => this.facade.responsibles(),
            save: (result: Parameters<TicketDetailFacade['handover']>[0]) =>
              mode === 'assign'
                ? this.facade.assignResponsible(result.incomingResponsibleId)
                : this.facade.handover(result),
          },
        })
        .afterClosed(),
    );
    if (saved === true) {
      this.notifier.success(mode === 'assign' ? 'Responsable asignado' : 'Relevo registrado');
    }
  }

  /**
   * Abre el mensaje para WhatsApp. Lo ven los cinco roles (copiar no cambia nada); «Registrar envío» solo se ofrece a quien
   * puede registrar comunicaciones.
   */
  protected async openMessage(): Promise<void> {
    await firstValueFrom(
      this.dialog
        .open(MessageDialog, {
          ...WIDE_DIALOG,
          data: {
            load: (options: Parameters<TicketDetailFacade['message']>[0]) =>
              this.facade.message(options),
            ...(this.can('COMMUNICATION') ? { record: () => this.recordCommunication() } : {}),
          },
        })
        .afterClosed(),
    );
  }

  /** Registra el envío real. Resuelve `true` si se registró, aunque la evidencia opcional no se haya podido cargar. */
  private async recordCommunication(): Promise<boolean> {
    const detail = this.facade.detail();
    if (detail === undefined) return false;
    const warning = await firstValueFrom(
      this.dialog
        .open(CommunicationDialog, {
          ...WIDE_DIALOG,
          data: {
            suggestedKind: suggestedCommunicationKind(detail.summary.state, detail.communications),
            save: (
              result: Parameters<TicketDetailFacade['recordCommunication']>[0],
              evidence: File | null,
            ) => this.facade.recordCommunication(result, evidence),
          },
        })
        .afterClosed(),
    );
    if (warning === undefined) return false;
    this.notifier.success('Envío registrado');
    if (warning) {
      this.notifier.error(
        `La evidencia no se pudo cargar: ${warning} El envío sí quedó registrado; adjúntela desde el detalle.`,
      );
    }
    return true;
  }

  /**
   * Cierra el ticket (FR-047). El diálogo lista lo que falta según la clase del elemento y el servidor decide: si aun así
   * rechaza el cierre, el diálogo muestra su lista y el ticket no cambia.
   */
  protected async closeTicket(): Promise<void> {
    const detail = this.facade.detail();
    const episode = detail?.episodes.at(-1);
    if (detail === undefined || episode === undefined) return;
    const closed = await firstValueFrom(
      this.dialog
        .open(CloseDialog, {
          ...WIDE_DIALOG,
          data: {
            elementClass: detail.summary.elementClass,
            episode,
            evidence: detail.evidence,
            communications: detail.communications,
            searchTickets: (text: string) => this.facade.searchTickets(text),
            save: (result: Parameters<TicketDetailFacade['closeTicket']>[0]) =>
              this.facade.closeTicket(result),
          },
        })
        .afterClosed(),
    );
    if (closed === true) {
      this.selected.set(null);
      this.notifier.success('Ticket cerrado');
    }
  }

  protected async reopen(): Promise<void> {
    const detail = this.facade.detail();
    if (detail === undefined) return;
    const reopened = await firstValueFrom(
      this.dialog
        .open(ReopenDialog, {
          ...WIDE_DIALOG,
          data: {
            code: detail.summary.code,
            save: (result: Parameters<TicketDetailFacade['reopenTicket']>[0]) =>
              this.facade.reopenTicket(result),
          },
        })
        .afterClosed(),
    );
    if (reopened === true) {
      this.selected.set(null);
      this.notifier.success('Ticket reabierto');
    }
  }

  protected async changePriority(): Promise<void> {
    const detail = this.facade.detail();
    if (detail === undefined) return;
    const saved = await firstValueFrom(
      this.dialog
        .open(PriorityDialog, {
          ...WIDE_DIALOG,
          data: {
            current: detail.summary.priority,
            save: (result: Parameters<TicketDetailFacade['changePriority']>[0]) =>
              this.facade.changePriority(result),
          },
        })
        .afterClosed(),
    );
    if (saved === true) this.notifier.success('Prioridad cambiada');
  }

  protected async reclassify(): Promise<void> {
    const detail = this.facade.detail();
    if (detail === undefined) return;
    const saved = await firstValueFrom(
      this.dialog
        .open(ReclassifyDialog, {
          ...WIDE_DIALOG,
          data: {
            elementClass: detail.summary.elementClass,
            current: detail.summary.incidentType,
            save: (result: Parameters<TicketDetailFacade['reclassify']>[0]) =>
              this.facade.reclassify(result),
          },
        })
        .afterClosed(),
    );
    if (saved === true) this.notifier.success('Incidencia reclasificada');
  }

  protected async correct(): Promise<void> {
    const detail = this.facade.detail();
    if (detail === undefined) return;
    const saved = await firstValueFrom(
      this.dialog
        .open(CorrectionDialog, {
          ...WIDE_DIALOG,
          data: {
            description: detail.description,
            reportOrigin: detail.reportOrigin as ReportOrigin,
            reportOriginDetail: detail.reportOriginDetail,
            save: (result: Parameters<TicketDetailFacade['correctData']>[0]) =>
              this.facade.correctData(result),
          },
        })
        .afterClosed(),
    );
    if (saved === true) this.notifier.success('Datos corregidos');
  }
}
