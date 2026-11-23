import { NgTemplateOutlet } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideEllipsisVertical, LucidePlus, LucideSearch } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { SessionStore } from '../../../../core/auth/session-store';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { WIDE_DIALOG } from '../../../../shared/ui/dialog-sizes';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { RefreshStatusIndicator } from '../../../../shared/ui/refresh-status/refresh-status';
import { TICKET_PAGE_SIZE, TicketSortField, TicketsFacade } from '../../application/tickets-facade';
import {
  BEFORE_START_MESSAGE,
  INCIDENT_TYPE_LABELS,
  networkLabel,
  PRIORITY_LABELS,
  TICKET_CONDITION_LABELS,
  TICKET_STATE_LABELS,
  Priority,
  IncidentType,
  TicketCondition,
} from '../../domain/incident-labels';
import { INCIDENT_EDITORS } from '../../domain/incident-permissions';
import { ageText, formatInstant } from '../../domain/incident-time';
import { MessageOptions } from '../../domain/incident-message';
import { MessageDialog } from '../message-dialog/message-dialog';
import { TicketCreateDialog } from '../ticket-create-dialog/ticket-create-dialog';
import { TicketStateBadge } from '../ticket-state-badge/ticket-state-badge';

interface Column {
  readonly field: TicketSortField;
  readonly label: string;
}

/** Listado de tickets (FR-061): busqueda, filtros, orden por columna y pagina; tarjetas en pantallas angostas. */
@Component({
  selector: 'sarc-tickets-page',
  imports: [
    MatMenuModule,
    NgTemplateOutlet,
    LucideEllipsisVertical,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    RouterLink,
    LucidePlus,
    LucideSearch,
    RefreshStatusIndicator,
    TicketStateBadge,
  ],
  providers: [TicketsFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  template: `
    <header class="page-header">
      <div>
        <h1>Tickets</h1>
        <p>Incidencias de equipos, del chatbot y de los elementos del sitio.</p>
        <sarc-refresh-status [status]="facade.refresh" />
      </div>
      @if (canEdit) {
        <div class="header-actions">
          <button matButton="filled" type="button" (click)="createTicket()">
            <svg lucidePlus [size]="18" aria-hidden="true"></svg>
            Nuevo ticket
          </button>
        </div>
      }
    </header>

    <section class="filters" aria-label="Filtros">
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Buscar</mat-label>
        <svg lucideSearch matPrefix [size]="18" class="prefix-icon" aria-hidden="true"></svg>
        <input
          matInput
          type="search"
          placeholder="Código o texto"
          [value]="facade.search()"
          (input)="onSearch($event)"
        />
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Estado</mat-label>
        <mat-select
          [value]="facade.state()"
          (selectionChange)="facade.filter('state', $event.value)"
        >
          <mat-option value="">Todos</mat-option>
          @for (state of states; track state) {
            <mat-option [value]="state">{{ stateLabels[state] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Tipo</mat-label>
        <mat-select
          [value]="facade.incidentType()"
          (selectionChange)="facade.filter('incidentType', $event.value)"
        >
          <mat-option value="">Todos</mat-option>
          @for (type of types; track type) {
            <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Prioridad</mat-label>
        <mat-select
          [value]="facade.priority()"
          (selectionChange)="facade.filter('priority', $event.value)"
        >
          <mat-option value="">Todas</mat-option>
          @for (priority of priorities; track priority) {
            <mat-option [value]="priority">{{ priorityLabels[priority] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Red</mat-label>
        <mat-select
          [value]="facade.network()"
          (selectionChange)="facade.filter('network', $event.value)"
        >
          <mat-option value="">Todas</mat-option>
          @for (network of networks; track network) {
            <mat-option [value]="network">{{ network }}</mat-option>
          }
          <mat-option value="NONE">Sin red</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Condición</mat-label>
        <mat-select
          [value]="facade.condition()"
          (selectionChange)="facade.filter('condition', $event.value)"
        >
          <mat-option value="">Todas</mat-option>
          @for (condition of conditions; track condition) {
            <mat-option [value]="condition">{{ conditionLabels[condition] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      @if (facade.hasFilters()) {
        <button matButton type="button" (click)="facade.clearFilters()">Limpiar filtros</button>
      }
    </section>

    @if (facade.notice() === 'TICKET_BEFORE_START') {
      <p class="notice" role="status">{{ beforeStart }}</p>
    }

    @if (facade.refreshFailed()) {
      <div class="refresh-alert" role="status">
        <p>No se pudo actualizar el listado. Se muestran los resultados anteriores.</p>
        <button matButton type="button" (click)="facade.load()">Reintentar</button>
      </div>
    }

    <section class="results" aria-label="Tickets" [attr.aria-busy]="facade.loading()">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando tickets" />
      }

      @if (facade.failed()) {
        <div class="state" role="alert">
          <p>{{ facade.errorMessage() || 'No se pudo cargar el listado.' }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar</button>
        </div>
      } @else if (!facade.loading() && facade.items().length === 0) {
        <div class="state">
          <p>No hay tickets que coincidan con los filtros.</p>
        </div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (ticket of facade.items(); track ticket.id) {
            <li class="card">
              <div class="card-head">
                <a [routerLink]="['..', 'tickets', ticket.id]">
                  <strong>{{ ticket.code }}</strong>
                </a>
                <sarc-ticket-state-badge [state]="ticket.state" />
                <ng-container
                  [ngTemplateOutlet]="rowMenu"
                  [ngTemplateOutletContext]="{ $implicit: ticket }"
                />
              </div>
              <span>{{ ticket.elementLabel }}</span>
              <span class="muted">
                {{ ticket.incidentTypeLabel }} · {{ priorityLabels[ticket.priority] }} ·
                {{ network(ticket.network) }}
              </span>
              <span class="muted">
                {{ ticket.responsible.name }} · {{ age(ticket.state, ticket.t0) }}
              </span>
            </li>
          }
        </ul>
      } @else {
        <div class="table-wrap">
          <table mat-table [dataSource]="facade.items()" [trackBy]="trackById">
            @for (column of columns; track column.field) {
              <ng-container [matColumnDef]="column.field">
                <th mat-header-cell *matHeaderCellDef [attr.aria-sort]="ariaSort(column.field)">
                  <button type="button" class="sort" (click)="facade.sortBy(column.field)">
                    {{ column.label }}
                    @if (facade.sortField() === column.field) {
                      <span aria-hidden="true">{{ facade.sortDescending() ? '↓' : '↑' }}</span>
                    }
                  </button>
                </th>
                <td mat-cell *matCellDef="let ticket">
                  @switch (column.field) {
                    @case ('code') {
                      <a [routerLink]="['..', 'tickets', ticket.id]">{{ ticket.code }}</a>
                    }
                    @case ('type') {
                      {{ ticket.incidentTypeLabel }}
                    }
                    @case ('network') {
                      {{ network(ticket.network) }}
                    }
                    @case ('state') {
                      <sarc-ticket-state-badge [state]="ticket.state" />
                    }
                    @case ('priority') {
                      {{ priorityText(ticket.priority) }}
                    }
                    @case ('responsible') {
                      {{ ticket.responsible.name }}
                    }
                    @case ('t0') {
                      {{ age(ticket.state, ticket.t0) }}
                    }
                    @case ('lastActivityAt') {
                      {{ instant(ticket.lastActivityAt) }}
                    }
                  }
                </td>
              </ng-container>
            }
            <ng-container matColumnDef="element">
              <th mat-header-cell *matHeaderCellDef>Elemento</th>
              <td mat-cell *matCellDef="let ticket">{{ ticket.elementLabel }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef><span class="sr-only">Acciones</span></th>
              <td mat-cell *matCellDef="let ticket">
                <ng-container
                  [ngTemplateOutlet]="rowMenu"
                  [ngTemplateOutletContext]="{ $implicit: ticket }"
                />
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayed"></tr>
            <tr mat-row *matRowDef="let row; columns: displayed"></tr>
          </table>
        </div>
      }

      <ng-template #rowMenu let-ticket>
        <button
          matIconButton
          type="button"
          [matMenuTriggerFor]="actionsMenu"
          [matMenuTriggerData]="{ ticket }"
          [attr.aria-label]="'Acciones de ' + ticket.code"
        >
          <svg lucideEllipsisVertical [size]="18" aria-hidden="true"></svg>
        </button>
      </ng-template>
      <mat-menu #actionsMenu="matMenu" xPosition="before">
        <ng-template matMenuContent let-ticket="ticket">
          <button mat-menu-item type="button" (click)="openMessage(ticket)">
            Mensaje para WhatsApp
          </button>
        </ng-template>
      </mat-menu>

      <mat-paginator
        [length]="facade.total()"
        [pageIndex]="facade.page()"
        [pageSize]="pageSize"
        [hidePageSize]="true"
        [showFirstLastButtons]="true"
        (page)="onPage($event)"
      />
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/list-page';

    :host {
      display: block;
      --sarc-filter-columns: repeat(auto-fit, minmax(12rem, 1fr));
    }

    .sort {
      padding: 0;
      border: 0;
      background: none;
      color: inherit;
      font: inherit;
      cursor: pointer;
    }

    .notice {
      margin: 0 0 var(--sarc-space-3);
      padding: var(--sarc-space-3) var(--sarc-space-4);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }

    .table-wrap {
      overflow-x: auto;
    }

    .cards {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .card {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-1);
      padding: var(--sarc-space-3) var(--sarc-space-4);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-medium);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }

    .card-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--sarc-space-2);
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class TicketsPage {
  protected readonly facade = inject(TicketsFacade);
  private readonly dialog = inject(MatDialog);
  protected readonly isHandset = injectIsHandset();
  protected readonly canEdit = inject(SessionStore).hasAnyRole(INCIDENT_EDITORS);

  protected readonly pageSize = TICKET_PAGE_SIZE;
  protected readonly beforeStart = BEFORE_START_MESSAGE;
  protected readonly stateLabels = TICKET_STATE_LABELS;
  protected readonly states = Object.keys(
    TICKET_STATE_LABELS,
  ) as (keyof typeof TICKET_STATE_LABELS)[];
  protected readonly typeLabels = INCIDENT_TYPE_LABELS;
  protected readonly types = Object.keys(INCIDENT_TYPE_LABELS) as IncidentType[];
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly priorities = Object.keys(PRIORITY_LABELS) as Priority[];
  protected readonly conditionLabels = TICKET_CONDITION_LABELS;
  protected readonly conditions = Object.keys(TICKET_CONDITION_LABELS) as TicketCondition[];
  protected readonly networks = ['KIA', 'JAC', 'CONECTZ'];

  /** El elemento afectado no se ordena (FR-061). */
  protected readonly columns: readonly Column[] = [
    { field: 'code', label: 'Código' },
    { field: 'type', label: 'Tipo' },
    { field: 'network', label: 'Red' },
    { field: 'state', label: 'Estado' },
    { field: 'priority', label: 'Prioridad' },
    { field: 'responsible', label: 'Responsable' },
    { field: 't0', label: 'Antigüedad' },
    { field: 'lastActivityAt', label: 'Actualizado' },
  ];
  protected readonly displayed = [
    'code',
    'element',
    'type',
    'network',
    'state',
    'priority',
    'responsible',
    't0',
    'lastActivityAt',
    'actions',
  ];

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.facade.load();
  }

  /** Registrar un ticket se hace en un diálogo, como «Nuevo equipo» en Inventario; al crear abre el ticket nuevo. */
  protected async createTicket(): Promise<void> {
    await firstValueFrom(
      this.dialog
        .open<TicketCreateDialog, undefined, string>(TicketCreateDialog, {
          ...WIDE_DIALOG,
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
  }

  /**
   * Abre el mensaje para WhatsApp de un ticket de la fila: lo pueden abrir los cinco roles y copiar no cambia nada.
   * Registrar el envío se hace desde el detalle del ticket, que es donde se puede actualizar.
   */
  protected async openMessage(ticket: { id: string }): Promise<void> {
    await firstValueFrom(
      this.dialog
        .open(MessageDialog, {
          ...WIDE_DIALOG,
          data: {
            load: (options: MessageOptions) => this.facade.message(ticket.id, options),
          },
        })
        .afterClosed(),
    );
  }

  protected trackById = (_: number, ticket: { id: string }) => ticket.id;

  protected priorityText(priority: Priority): string {
    return PRIORITY_LABELS[priority];
  }

  protected network = networkLabel;
  protected instant = formatInstant;

  /** La antigüedad solo tiene sentido en un ticket abierto: desde T0 hasta ahora. */
  protected age(state: 'OPEN' | 'CLOSED', t0: string): string {
    return state === 'OPEN' ? ageText(t0) : '—';
  }

  protected ariaSort(field: TicketSortField): 'ascending' | 'descending' | 'none' {
    if (this.facade.sortField() !== field) return 'none';
    return this.facade.sortDescending() ? 'descending' : 'ascending';
  }

  protected onSearch(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.facade.searchFor(text), 300);
  }

  protected onPage(event: PageEvent): void {
    this.facade.goToPage(event.pageIndex);
  }
}
