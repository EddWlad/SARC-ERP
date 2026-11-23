import { DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { LucideFileSpreadsheet, LucideFileText } from '@lucide/angular';

import { Problem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { ChartSeries, ChartView } from '../../../../shared/ui/chart/chart-view';
import { PeriodFilter } from '../../../../shared/ui/period/period-filter/period-filter';
import { RefreshStatusIndicator } from '../../../../shared/ui/refresh-status/refresh-status';
import { DashboardFacade } from '../../application/dashboard-facade';
import {
  DURATION_PAIR_LABELS,
  ELEMENT_CLASS_LABELS,
  INCIDENT_TYPE_LABELS,
  PRIORITY_LABELS,
  REFERENCE_DISCLAIMER,
  REFERENCE_MEASURE_LABELS,
  REPORT_ORIGIN_LABELS,
} from '../../domain/incident-labels';
import { formatInstant } from '../../domain/incident-time';

type Dimension = 'type' | 'priority' | 'class' | 'origin' | 'network' | 'equipment';

/**
 * Tablero de gestión de incidencias (FR-080 a FR-086): cifras con su denominador, referencias como referencias y no como
 * SLA, gráficos y repartos sin ponderar, y enlaces a Uptime e Intermitencias en vez de cifras propias de disponibilidad
 * (P-13). Lo ven y lo descargan los cinco roles; no modifica nada.
 */
@Component({
  selector: 'sarc-incidents-dashboard-page',
  imports: [
    ChartView,
    DecimalPipe,
    LucideFileSpreadsheet,
    LucideFileText,
    MatButtonModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatSelectModule,
    PeriodFilter,
    RefreshStatusIndicator,
    RouterLink,
  ],
  providers: [DashboardFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Tablero de incidencias</h1>
        <p>Indicadores de gestión del período, con su denominador y sin ponderar.</p>
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
      <sarc-period-filter [period]="facade.period" />
    </section>

    <section class="filters" aria-label="Filtros">
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
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Clase de elemento</mat-label>
        <mat-select
          [value]="facade.elementClass()"
          (selectionChange)="facade.filter('elementClass', $event.value)"
        >
          <mat-option value="">Todas</mat-option>
          @for (item of classes; track item) {
            <mat-option [value]="item">{{ classLabels[item] }}</mat-option>
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
          @for (item of types; track item) {
            <mat-option [value]="item">{{ typeLabels[item] }}</mat-option>
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
          @for (item of priorities; track item) {
            <mat-option [value]="item">{{ priorityLabels[item] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      @if (facade.hasFilters()) {
        <button matButton type="button" (click)="facade.clearFilters()">Limpiar filtros</button>
      }
    </section>

    <div class="progress" aria-hidden="true">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>

    @if (facade.refreshFailed()) {
      <div class="refresh-alert" role="status">
        <p>No se pudo actualizar el tablero. Se muestran las cifras anteriores.</p>
        <button matButton type="button" (click)="facade.load()">Reintentar</button>
      </div>
    }

    @if (facade.failed()) {
      <div class="state" role="alert">
        <p>{{ facade.errorMessage() || 'No se pudo cargar el tablero.' }}</p>
        <button matButton="tonal" type="button" (click)="facade.load()">Reintentar</button>
      </div>
    } @else if (facade.summary(); as summary) {
      <p class="cut-off">Corte: {{ instant(summary.cutOff) }} · Zona horaria: America/Guayaquil</p>

      <section aria-label="Resumen" [attr.aria-busy]="facade.loading()">
        <ul class="kpi-grid">
          <li class="kpi-tile">
            <span class="kpi-value">{{ summary.totals.uniqueTickets }}</span>
            <span class="kpi-label">Tickets únicos</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ summary.totals.episodes }}</span>
            <span class="kpi-label">Episodios</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ summary.openTicketsAtCutOff }}</span>
            <span class="kpi-label">Abiertos al corte</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ summary.totals.closedEpisodes }}</span>
            <span class="kpi-label">Episodios cerrados</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ summary.totals.reopened }}</span>
            <span class="kpi-label">Reabiertos</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ summary.totals.critical }}</span>
            <span class="kpi-label">Críticos</span>
          </li>
        </ul>
        <p class="dashboard-note">
          Cerrados por duplicado o error, aparte y fuera de los promedios:
          <strong>{{ summary.totals.excluded }}</strong>
        </p>
      </section>

      @if (facade.conditions(); as conditions) {
        <section class="panel" aria-labelledby="conditions-title">
          <h2 id="conditions-title">Abiertos ahora, según su avance</h2>
          <ul>
            <li class="metric-row">
              <span>Pendientes de validación (sin T1)</span>
              <strong>{{ conditions.pendingValidation }}</strong>
            </li>
            <li class="metric-row">
              <span>En atención (con T2 y sin T3)</span>
              <strong>{{ conditions.inAttention }}</strong>
            </li>
            <li class="metric-row">
              <span>Recuperados en observación (con T3)</span>
              <strong>{{ conditions.recoveredObserving }}</strong>
            </li>
          </ul>
          <p class="dashboard-note">
            Son los tickets abiertos hoy, con los filtros elegidos.
            <a routerLink="../tickets">Ver el listado</a> con su antigüedad, responsable y última
            actualización.
          </p>
        </section>
      }

      <section class="panel" aria-labelledby="times-title">
        <h2 id="times-title">Indicadores de tiempo</h2>
        <div class="table-wrap">
          <table class="plain">
            <caption class="sr-only">
              Promedios por medida, con su denominador y los episodios que no entran
            </caption>
            <thead>
              <tr>
                <th scope="col">Medida</th>
                <th scope="col">Promedio (min)</th>
                <th scope="col">n</th>
                <th scope="col">No aplica</th>
                <th scope="col">Sin dato</th>
                <th scope="col">Abiertos o pendientes</th>
              </tr>
            </thead>
            <tbody>
              @for (measure of summary.measures; track measure.pair) {
                <tr>
                  <th scope="row">{{ pairLabels[measure.pair] || measure.pair }}</th>
                  <td>
                    @if (measure.averageMinutes !== null && measure.averageMinutes !== undefined) {
                      {{ measure.averageMinutes | number: '1.1-1' }}
                    } @else {
                      —
                    }
                  </td>
                  <td>{{ measure.n }}</td>
                  <td>{{ measure.notApplicable }}</td>
                  <td>{{ measure.noData }}</td>
                  <td>{{ measure.open }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="dashboard-note">
          Validaciones dentro de la referencia:
          @if (summary.validation.percent !== null && summary.validation.percent !== undefined) {
            <strong>{{ summary.validation.percent | number: '1.1-1' }} %</strong>
          } @else {
            <strong>—</strong>
          }
          (n = {{ summary.validation.n }}). Un hito sin hora nunca cuenta como cero.
        </p>
      </section>

      <section class="panel" aria-labelledby="references-title">
        <h2 id="references-title">Referencias operativas</h2>
        <p class="dashboard-note">
          {{ disclaimer }}. Versión de referencias: {{ summary.referenceVersions || '—' }}.
        </p>
        @if (summary.references.length === 0) {
          <p class="dashboard-note">Ningún episodio con referencia evaluable en el período.</p>
        } @else {
          <div class="table-wrap">
            <table class="plain">
              <thead>
                <tr>
                  <th scope="col">Medida</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Dentro</th>
                  <th scope="col">Fuera</th>
                  <th scope="col">No evaluables</th>
                </tr>
              </thead>
              <tbody>
                @for (item of summary.references; track item.measure + item.type) {
                  <tr>
                    <th scope="row">{{ measureLabels[item.measure] || item.measure }}</th>
                    <td>{{ typeLabel(item.type) }}</td>
                    <td>{{ item.within }}</td>
                    <td>{{ item.outside }}</td>
                    <td>{{ item.notEvaluable }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
        @if (summary.withoutReference.length > 0) {
          <p class="dashboard-note">
            Sin referencia aprobada, se muestra el tiempo sin juicio:
            {{ withoutReferenceText() }}.
          </p>
        }
        @if (summary.deviations.length > 0) {
          <h3>Fuera de referencia</h3>
          <ul>
            @for (item of summary.deviations; track item.code + item.episode + item.measure) {
              <li class="metric-row">
                <span>
                  <a [routerLink]="['..', 'tickets']" [queryParams]="{ search: item.code }">{{
                    item.code
                  }}</a>
                  · episodio {{ item.episode }} · {{ measureLabels[item.measure] || item.measure }}
                </span>
                <span class="muted">{{ item.justification || 'Sin justificación' }}</span>
              </li>
            }
          </ul>
        }
      </section>

      @if (summary.totals.episodes === 0) {
        <p class="dashboard-note">Sin datos en el período.</p>
      } @else {
        <section class="charts" aria-label="Gráficos sin ponderar">
          @if (dayLabels().length > 0) {
            <section class="panel wide" aria-labelledby="day-title">
              <h2 id="day-title">Episodios por fecha de T0 (n = {{ total(summary.byDay) }})</h2>
              <sarc-chart-view
                [format]="countFormat"
                kind="bar"
                [labels]="dayLabels()"
                [series]="daySeries()"
                description="Episodios por día de inicio (T0), en el período"
              />
            </section>
          }
          <section class="panel" aria-labelledby="type-title">
            <h2 id="type-title">Por tipo (n = {{ total(summary.byType) }})</h2>
            <sarc-chart-view
              [format]="countFormat"
              kind="bar"
              [horizontal]="true"
              [labels]="typeLabelsList()"
              [series]="typeSeries()"
              description="Episodios por tipo de incidencia"
            />
          </section>
          <section class="panel" aria-labelledby="priority-title">
            <h2 id="priority-title">Por prioridad (n = {{ total(summary.byPriority) }})</h2>
            <sarc-chart-view
              [format]="countFormat"
              kind="bar"
              [labels]="priorityLabelsList()"
              [series]="prioritySeries()"
              description="Episodios por prioridad"
            />
          </section>
          @if (summary.repeaters.length > 0) {
            <section class="panel" aria-labelledby="repeaters-title">
              <h2 id="repeaters-title">
                Equipos con más episodios (n = {{ total(summary.repeaters) }})
              </h2>
              <sarc-chart-view
                [format]="countFormat"
                kind="bar"
                [horizontal]="true"
                [labels]="repeaterLabels()"
                [series]="repeaterSeries()"
                description="Equipos con más episodios en el período"
              />
              <p class="dashboard-note">
                Ordenados por número de episodios; no hay umbral aprobado de recurrencia.
              </p>
            </section>
          }
        </section>

        <section class="groups" aria-label="Repartos">
          @for (group of groups(); track group.title) {
            <section class="panel" [attr.aria-label]="group.title">
              <h2>{{ group.title }}</h2>
              <ul>
                @for (row of group.rows; track row.key) {
                  <li class="metric-row">
                    <span>{{ row.label }}</span>
                    <strong>{{ row.count }}</strong>
                  </li>
                }
              </ul>
            </section>
          }
          <section class="panel" aria-label="Visitas técnicas">
            <h2>Visitas técnicas</h2>
            <ul>
              <li class="metric-row">
                <span>Solicitadas</span><strong>{{ summary.visits.requested }}</strong>
              </li>
              <li class="metric-row">
                <span>Confirmadas</span><strong>{{ summary.visits.confirmed }}</strong>
              </li>
              <li class="metric-row">
                <span>Canceladas o aplazadas</span
                ><strong>{{ summary.visits.cancelledOrPostponed }}</strong>
              </li>
              <li class="metric-row">
                <span>Ejecutadas</span><strong>{{ summary.visits.executed }}</strong>
              </li>
            </ul>
          </section>
        </section>
      }

      <section class="panel" aria-labelledby="sources-title">
        <h2 id="sources-title">Disponibilidad</h2>
        <p class="dashboard-note">
          Este tablero no calcula disponibilidad: consulte la
          <a routerLink="/uptime">disponibilidad oficial validada en Uptime</a> y el
          <a routerLink="/intermitencias"
            >tiempo fuera de línea y la disponibilidad estimada de prueba en Intermitencias</a
          >.
        </p>
      </section>
    }
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/dashboard';

    :host {
      display: block;
      --sarc-filter-columns: repeat(auto-fit, minmax(12rem, 1fr));
    }

    .period {
      margin-bottom: var(--sarc-space-3);
    }

    .progress {
      height: 4px;
      margin-bottom: var(--sarc-space-3);
    }

    .cut-off {
      margin: 0 0 var(--sarc-space-3);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }

    section.panel,
    .charts,
    .groups {
      margin-top: var(--sarc-space-4);
    }

    .charts,
    .groups {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr));
      gap: var(--sarc-space-4);
    }

    .charts .panel,
    .groups .panel {
      margin-top: 0;
    }

    .wide {
      grid-column: 1 / -1;
    }

    ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    h3 {
      margin: var(--sarc-space-3) 0;
      font: var(--mat-sys-title-small);
    }

    .table-wrap {
      overflow-x: auto;
    }

    .plain {
      width: 100%;
      border-collapse: collapse;

      th,
      td {
        padding: var(--sarc-space-2) var(--sarc-space-3);
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        text-align: left;
      }

      td {
        font-variant-numeric: tabular-nums;
      }
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
})
export class DashboardPage {
  protected readonly facade = inject(DashboardFacade);
  private readonly notifier = inject(Notifier);

  protected readonly networks = ['KIA', 'JAC', 'CONECTZ'];
  protected readonly classLabels = ELEMENT_CLASS_LABELS;
  protected readonly classes = Object.keys(
    ELEMENT_CLASS_LABELS,
  ) as (keyof typeof ELEMENT_CLASS_LABELS)[];
  protected readonly typeLabels = INCIDENT_TYPE_LABELS;
  protected readonly types = Object.keys(
    INCIDENT_TYPE_LABELS,
  ) as (keyof typeof INCIDENT_TYPE_LABELS)[];
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly priorities = Object.keys(PRIORITY_LABELS) as (keyof typeof PRIORITY_LABELS)[];
  /** El nombre de T4−T3 es el de la especificación (FR-080), igual que en el PDF y el Excel. */
  protected readonly pairLabels: Readonly<Record<string, string>> = {
    ...DURATION_PAIR_LABELS,
    'T4-T3': 'Recuperación a cierre (T4 − T3)',
  };
  protected readonly measureLabels = REFERENCE_MEASURE_LABELS as Readonly<Record<string, string>>;
  protected readonly disclaimer = REFERENCE_DISCLAIMER;
  protected instant = formatInstant;
  /** Son conteos de episodios: el eje no muestra valores con decimales. */
  protected readonly countFormat = (value: number): string =>
    Number.isInteger(value) ? String(value) : '';

  protected typeLabel(type: string): string {
    return this.label('type', type);
  }

  private label(dimension: Dimension, key: string): string {
    const tables: Record<string, Readonly<Record<string, string>>> = {
      type: INCIDENT_TYPE_LABELS,
      priority: PRIORITY_LABELS,
      class: ELEMENT_CLASS_LABELS,
      origin: REPORT_ORIGIN_LABELS,
    };
    return tables[dimension]?.[key] ?? key;
  }

  protected total(rows: readonly { count: number }[]): number {
    return rows.reduce((sum, row) => sum + row.count, 0);
  }

  protected readonly withoutReferenceText = computed(() =>
    (this.facade.summary()?.withoutReference ?? []).map((type) => this.typeLabel(type)).join(', '),
  );

  /** Dias del periodo como «05/10», en el orden en que llegan. */
  protected readonly dayLabels = computed(() =>
    (this.facade.summary()?.byDay ?? []).map(
      (row) => `${row.key.slice(8, 10)}/${row.key.slice(5, 7)}`,
    ),
  );
  protected readonly daySeries = computed(() =>
    this.series('Episodios', this.facade.summary()?.byDay ?? []),
  );
  protected readonly typeLabelsList = computed(() =>
    (this.facade.summary()?.byType ?? []).map((row) => this.label('type', row.key)),
  );
  protected readonly typeSeries = computed(() =>
    this.series('Episodios', this.facade.summary()?.byType ?? []),
  );
  protected readonly priorityLabelsList = computed(() =>
    (this.facade.summary()?.byPriority ?? []).map((row) => this.label('priority', row.key)),
  );
  protected readonly prioritySeries = computed(() =>
    this.series('Episodios', this.facade.summary()?.byPriority ?? []),
  );
  protected readonly repeaterLabels = computed(() =>
    (this.facade.summary()?.repeaters ?? []).map((row) => row.key),
  );
  protected readonly repeaterSeries = computed(() =>
    this.series('Episodios', this.facade.summary()?.repeaters ?? []),
  );

  /** Los repartos como tablas de texto, para que la cifra nunca dependa solo de una gráfica. */
  protected readonly groups = computed(() => {
    const summary = this.facade.summary();
    if (summary === null) return [];
    const rows = (dimension: Dimension, list: readonly { key: string; count: number }[]) =>
      list.map((row) => ({
        key: row.key,
        label: this.label(dimension, row.key),
        count: row.count,
      }));
    return [
      { title: 'Por tipo', rows: rows('type', summary.byType) },
      { title: 'Por red (conservada en T0)', rows: rows('network', summary.byNetwork) },
      { title: 'Por clase de elemento', rows: rows('class', summary.byClass) },
      { title: 'Por equipo', rows: rows('equipment', summary.byEquipment) },
      { title: 'Por prioridad', rows: rows('priority', summary.byPriority) },
      { title: 'Por origen del aviso', rows: rows('origin', summary.byOrigin) },
    ].filter((group) => group.rows.length > 0);
  });

  private series(label: string, rows: readonly { count: number }[]): ChartSeries {
    return { label, values: rows.map((row) => row.count), colors: 'series-1' };
  }

  protected async download(format: ReportFormat): Promise<void> {
    try {
      await this.facade.downloadReport(format);
    } catch (error) {
      this.notifier.error(
        (error as Partial<Problem> | undefined)?.detail || 'No se pudo generar el informe.',
      );
    }
  }
}
