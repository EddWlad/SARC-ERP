import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideFileSpreadsheet, LucideFileText } from '@lucide/angular';

import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Problem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { PeriodFilter } from '../../../../shared/ui/period/period-filter/period-filter';
import { RefreshStatusIndicator } from '../../../../shared/ui/refresh-status/refresh-status';
import { CONTROL_PAGE_SIZE, ControlFacade } from '../../application/control-facade';
import { TICKET_STATE_LABELS, TicketState } from '../../domain/incident-labels';
import { formatInstant } from '../../domain/incident-time';
import { TicketStateBadge } from '../ticket-state-badge/ticket-state-badge';

/**
 * Informe «Control de tickets» (FR-070 a FR-077): la tabla de tres columnas del PDF, con «Todo el historial» de inicio,
 * el estado de cada ticket al final del periodo, el pie con los totales y la descarga en PDF y Excel. Lo ven y lo
 * descargan los cinco roles; no modifica nada.
 */
@Component({
  selector: 'sarc-control-page',
  imports: [
    LucideFileSpreadsheet,
    LucideFileText,
    MatButtonModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    PeriodFilter,
    RefreshStatusIndicator,
    RouterLink,
    TicketStateBadge,
  ],
  providers: [ControlFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  template: `
    <header class="page-header">
      <div>
        <h1>Control de tickets</h1>
        <p>Registro operativo de los tickets, con su estado al final del período.</p>
        <sarc-refresh-status [status]="facade.refresh" />
      </div>
      <div class="header-actions">
        <button
          matButton="outlined"
          type="button"
          [disabled]="facade.downloading() !== undefined"
          (click)="download('PDF')"
        >
          <svg lucideFileText [size]="18" aria-hidden="true"></svg>
          {{ facade.downloading() === 'PDF' ? 'Generando...' : 'Descargar PDF' }}
        </button>
        <button
          matButton="outlined"
          type="button"
          [disabled]="facade.downloading() !== undefined"
          (click)="download('XLSX')"
        >
          <svg lucideFileSpreadsheet [size]="18" aria-hidden="true"></svg>
          {{ facade.downloading() === 'XLSX' ? 'Generando...' : 'Descargar Excel' }}
        </button>
      </div>
    </header>

    <section class="period" aria-label="Período">
      <sarc-period-filter [period]="facade.period" [allowAllHistory]="true" />
    </section>

    <section class="filters" aria-label="Filtros">
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Estado al final del período</mat-label>
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
        <mat-label>Red</mat-label>
        <mat-select
          [value]="facade.network()"
          (selectionChange)="facade.filter('network', $event.value)"
        >
          <mat-option value="">Todas</mat-option>
          @for (network of networks; track network) {
            <mat-option [value]="network">{{ network }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      @if (facade.hasFilters()) {
        <button matButton type="button" (click)="facade.clearFilters()">Limpiar filtros</button>
      }
    </section>

    @if (facade.cutOff(); as cutOff) {
      <p class="cut-off">Corte: {{ instant(cutOff) }} · Zona horaria: America/Guayaquil</p>
    }

    @if (facade.refreshFailed()) {
      <div class="refresh-alert" role="status">
        <p>No se pudo actualizar el informe. Se muestran los resultados anteriores.</p>
        <button matButton type="button" (click)="facade.load()">Reintentar</button>
      </div>
    }

    <section class="results" aria-label="Tickets del informe" [attr.aria-busy]="facade.loading()">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando el informe" />
      }

      @if (facade.failed()) {
        <div class="state" role="alert">
          <p>{{ facade.errorMessage() || 'No se pudo cargar el informe.' }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar</button>
        </div>
      } @else if (!facade.loading() && facade.rows().length === 0) {
        <div class="state">
          <p>Sin tickets en el período</p>
        </div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (row of facade.rows(); track row.id) {
            <li class="card">
              <div class="card-head">
                <a [routerLink]="['..', 'tickets', row.id]">
                  <strong>{{ row.code }}</strong>
                </a>
                <sarc-ticket-state-badge [state]="row.state" />
              </div>
              <span>{{ row.elementLabel }}</span>
            </li>
          }
        </ul>
      } @else {
        <div class="table-wrap">
          <table mat-table [dataSource]="facade.rows()" [trackBy]="trackById">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Número de ticket</th>
              <td mat-cell *matCellDef="let row">
                <a [routerLink]="['..', 'tickets', row.id]">{{ row.code }}</a>
              </td>
            </ng-container>
            <ng-container matColumnDef="element">
              <th mat-header-cell *matHeaderCellDef>Cargador o electrolinera</th>
              <td mat-cell *matCellDef="let row">{{ row.elementLabel }}</td>
            </ng-container>
            <ng-container matColumnDef="state">
              <th mat-header-cell *matHeaderCellDef>Estado del ticket</th>
              <td mat-cell *matCellDef="let row">
                <sarc-ticket-state-badge [state]="row.state" />
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayed"></tr>
            <tr mat-row *matRowDef="let row; columns: displayed"></tr>
          </table>
        </div>
      }

      @if (facade.rows().length > 0) {
        <p class="totals" aria-live="polite">
          Total: {{ facade.total() }} · Abiertos: {{ facade.open() }} · Cerrados:
          {{ facade.closed() }}
        </p>
      }

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

    .period {
      margin-bottom: var(--sarc-space-3);
    }

    .cut-off,
    .totals {
      margin: 0 0 var(--sarc-space-3);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }

    .totals {
      margin: var(--sarc-space-3) 0 0;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-label-large);
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

    .card-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--sarc-space-2);
    }
  `,
})
export class ControlPage {
  protected readonly facade = inject(ControlFacade);
  private readonly notifier = inject(Notifier);
  protected readonly isHandset = injectIsHandset();

  protected readonly pageSize = CONTROL_PAGE_SIZE;
  protected readonly stateLabels = TICKET_STATE_LABELS;
  protected readonly states = Object.keys(TICKET_STATE_LABELS) as TicketState[];
  /** El informe solo filtra por las redes de carga: «Sin red» no aplica. */
  protected readonly networks = ['KIA', 'JAC', 'CONECTZ'];
  protected readonly displayed = ['code', 'element', 'state'];
  protected instant = formatInstant;

  protected trackById = (_: number, row: { id: string }) => row.id;

  protected async download(format: ReportFormat): Promise<void> {
    try {
      await this.facade.downloadReport(format);
    } catch (error) {
      this.notifier.error(
        (error as Partial<Problem> | undefined)?.detail || 'No se pudo generar el informe.',
      );
    }
  }

  protected onPage(event: PageEvent): void {
    this.facade.goToPage(event.pageIndex);
  }
}
