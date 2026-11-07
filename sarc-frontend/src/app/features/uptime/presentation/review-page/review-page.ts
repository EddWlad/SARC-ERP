import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideCircleCheck } from '@lucide/angular';

import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { ReviewFacade } from '../../application/review-facade';
import {
  TEMPORAL_WORK_LABELS,
  TEMPORAL_OUTCOME_LABELS,
  MeasurementQueue,
  OBSERVATION_LABELS,
  ObservationCode,
  PERIOD_TYPE_LABELS,
  PeriodType,
  SKIP_REASON_LABELS,
} from '../../domain/uptime-labels';

const QUEUE_LABELS: Readonly<Record<MeasurementQueue, string>> = {
  PENDING: 'Pendientes',
  OBSERVED: 'Observadas',
  TEMPORAL_RECONCILIATION: 'Conciliación temporal',
};

/** Cola de mediciones por validar. */
@Component({
  selector: 'sarc-review-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCheckboxModule,
    MatChipsModule,
    MatProgressBarModule,
    MatTableModule,
    RouterLink,
    LucideCircleCheck,
  ],
  providers: [ReviewFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Revisión de mediciones</h1>
        <p>Solo las mediciones validadas alimentan los indicadores oficiales.</p>
      </div>
      @if (facade.selectable().length > 0) {
        <div class="header-actions">
          <button
            matButton="filled"
            type="button"
            [disabled]="
              facade.selected().size === 0 ||
              facade.busy() ||
              (facade.queue() === 'TEMPORAL_RECONCILIATION' &&
                (facade.work()?.status === 'RUNNING' || facade.work()?.status === 'QUEUED'))
            "
            (click)="facade.validateSelected()"
          >
            <svg lucideCircleCheck [size]="18"></svg>
            {{
              facade.queue() === 'TEMPORAL_RECONCILIATION'
                ? 'Comprobar evidencia (bloques de 500)'
                : 'Validar selección'
            }}
            ({{ facade.selected().size }})
          </button>
        </div>
      }
    </header>

    <div class="toolbar">
      <mat-chip-listbox
        aria-label="Cola de revisión"
        [value]="facade.queue()"
        (change)="changeQueue($event.value)"
      >
        @for (queue of queues; track queue) {
          <mat-chip-option [value]="queue" [selectable]="facade.queue() !== queue">
            {{ queueLabels[queue] }}
          </mat-chip-option>
        }
      </mat-chip-listbox>
      @if (isHandset() && facade.selectable().length > 0) {
        <mat-checkbox
          [checked]="allSelected()"
          [indeterminate]="facade.selected().size > 0 && !allSelected()"
          (change)="$event.checked ? facade.selectAll() : facade.clearSelection()"
        >
          Seleccionar todas
        </mat-checkbox>
      }
    </div>

    @if (facade.work(); as work) {
      <section class="outcome" aria-label="Transición temporal" role="status">
        <h2>Comprobación de evidencia temporal</h2>
        <p>
          {{ workLabels[work.status] }} · {{ work.processed }} de {{ work.total }} procesadas ·
          Intento {{ work.attempt }}
        </p>
        @if (work.failureCode) {
          <p>{{ work.failureCode }}. El trabajo se conserva para reintentar.</p>
        }
        @if (work.status === 'FAILED' || work.status === 'INTERRUPTED') {
          <button matButton type="button" [disabled]="facade.busy()" (click)="facade.retryWork()">
            Reintentar trabajo
          </button>
        }
        <ul>
          @for (result of work.results.content; track result.id) {
            <li>
              <a [routerLink]="['/uptime/mediciones', result.id]">Medición</a>:
              {{ outcomeLabels[result.outcome] }}
            </li>
          }
        </ul>
        <button
          matButton
          type="button"
          [disabled]="facade.workPage() === 0"
          (click)="facade.readWork(facade.workPage() - 1)"
        >
          Anteriores
        </button>
        <button
          matButton
          type="button"
          [disabled]="facade.workPage() + 1 >= work.results.totalPages"
          (click)="facade.readWork(facade.workPage() + 1)"
        >
          Siguientes
        </button>
        <button matButton type="button" (click)="facade.load()">Actualizar cola</button>
      </section>
    }
    @if (facade.result(); as result) {
      <div role="status" class="outcome">
        <p>
          {{ quantities.amount(result.validated, 'validada', 'validadas') }},
          {{ quantities.amount(result.skipped.length, 'omitida', 'omitidas') }}.
        </p>
        @if (result.skipped.length > 0) {
          <ul>
            @for (skipped of result.skipped; track skipped.id) {
              <li>
                <a [routerLink]="['/uptime/mediciones', skipped.id]">Medición</a>:
                {{ reasons[skipped.reason] }}
              </li>
            }
          </ul>
        }
      </div>
    }

    <section class="results" aria-label="Mediciones" [attr.aria-busy]="facade.loading()">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando mediciones" />
      }
      @if (facade.error(); as error) {
        <div class="state" role="alert">
          <p>{{ error }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar</button>
        </div>
      } @else if (!facade.loading() && facade.items().length === 0) {
        <div class="state"><p>No hay mediciones en esta cola.</p></div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (item of facade.items(); track item.id) {
            <li class="card">
              <div class="card-head">
                <mat-checkbox
                  [aria-label]="'Seleccionar ' + item.equipmentName + ' conector ' + item.ordinal"
                  [checked]="facade.selected().has(item.id)"
                  [disabled]="
                    facade.queue() !== 'TEMPORAL_RECONCILIATION' && !!item.observations?.length
                  "
                  (change)="facade.toggle(item.id)"
                />
                <a class="link grow" [routerLink]="['/uptime/mediciones', item.id]">
                  <strong>{{ item.equipmentName }} · Conector {{ item.ordinal }}</strong>
                </a>
                <strong class="uptime">{{ quantities.percentage(item.uptimePercent) }}</strong>
              </div>
              <span class="muted">
                {{ item.periodType ? periods[item.periodType] : 'Por conciliar' }} ·
                @if (item.temporalPrecision === 'KNOWN_RANGE') {
                  {{ item.periodStart | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }} a
                  {{ item.periodEnd | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }}
                } @else {
                  Horario real no informado
                }
                @if (item.referenceWeek) {
                  · Referencia {{ item.referenceWeek }}
                }
              </span>
              @for (code of item.observations; track code) {
                <span class="badge badge-warn wrap">{{ observations[code] }}</span>
              }
            </li>
          }
        </ul>
      } @else {
        <table mat-table [dataSource]="facade.items()" class="table">
          <ng-container matColumnDef="select">
            <th mat-header-cell *matHeaderCellDef>
              <mat-checkbox
                aria-label="Seleccionar todas"
                [checked]="allSelected()"
                [indeterminate]="facade.selected().size > 0 && !allSelected()"
                [disabled]="facade.selectable().length === 0"
                (change)="$event.checked ? facade.selectAll() : facade.clearSelection()"
              />
            </th>
            <td mat-cell *matCellDef="let item">
              <mat-checkbox
                [aria-label]="'Seleccionar ' + item.equipmentName + ' conector ' + item.ordinal"
                [checked]="facade.selected().has(item.id)"
                [disabled]="
                  facade.queue() !== 'TEMPORAL_RECONCILIATION' && !!item.observations?.length
                "
                (change)="facade.toggle(item.id)"
              />
            </td>
          </ng-container>
          <ng-container matColumnDef="measurement">
            <th mat-header-cell *matHeaderCellDef>Medición</th>
            <td mat-cell *matCellDef="let item">
              <a class="link" [routerLink]="['/uptime/mediciones', item.id]">
                <strong>{{ item.equipmentName }} · Conector {{ item.ordinal }}</strong>
              </a>
              @for (code of item.observations; track code) {
                <span class="muted block warning">{{ observationLabel(code) }}</span>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="period">
            <th mat-header-cell *matHeaderCellDef>Período</th>
            <td mat-cell *matCellDef="let item">
              {{ periodLabel(item.periodType) }}
              <span class="muted block">
                @if (item.temporalPrecision === 'KNOWN_RANGE') {
                  {{ item.periodStart | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }} a
                  {{ item.periodEnd | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }}
                } @else {
                  Horario real no informado
                }
                @if (item.referenceWeek) {
                  · Referencia {{ item.referenceWeek }}
                }
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="uptime">
            <th mat-header-cell *matHeaderCellDef class="number">Uptime</th>
            <td mat-cell *matCellDef="let item" class="number">
              <strong class="uptime">{{ quantities.percentage(item.uptimePercent) }}</strong>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      }
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/imports/import-history';

    :host {
      display: block;
    }

    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-4);
    }

    .outcome {
      margin-bottom: var(--sarc-space-4);

      p {
        margin: 0;
      }

      ul {
        margin: var(--sarc-space-2) 0 0;
        padding-inline-start: var(--sarc-space-4);
      }
    }

    .card-head {
      align-items: center;
    }

    .grow {
      flex: 1 1 auto;
      min-width: 0;
    }

    .wrap {
      justify-self: start;
      align-self: flex-start;
      white-space: normal;
    }

    .warning {
      color: var(--mat-sys-error);
    }

    .uptime {
      color: var(--mat-sys-primary);
      white-space: nowrap;
    }
  `,
})
export class ReviewPage implements OnInit {
  protected readonly workLabels = TEMPORAL_WORK_LABELS;
  protected readonly outcomeLabels = TEMPORAL_OUTCOME_LABELS;
  protected readonly facade = inject(ReviewFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly periods = PERIOD_TYPE_LABELS;
  protected readonly observations = OBSERVATION_LABELS;
  protected readonly reasons = SKIP_REASON_LABELS;
  protected readonly queueLabels = QUEUE_LABELS;
  protected readonly queues = Object.keys(QUEUE_LABELS) as MeasurementQueue[];
  protected readonly columns = ['select', 'measurement', 'period', 'uptime'];
  protected readonly allSelected = computed(
    () =>
      this.facade.selected().size > 0 &&
      this.facade.selected().size === this.facade.selectable().length,
  );

  protected observationLabel(code: ObservationCode): string {
    return OBSERVATION_LABELS[code];
  }

  protected periodLabel(type: PeriodType): string {
    return PERIOD_TYPE_LABELS[type];
  }

  ngOnInit(): void {
    void this.facade.load();
    void this.facade.readWork();
  }

  protected changeQueue(queue: MeasurementQueue | undefined): void {
    // Un chip ya elegido no se deselecciona: siempre hay una cola.
    if (queue === undefined) return;
    this.facade.queue.set(queue);
    this.facade.clearSelection();
    this.facade.result.set(null);
    void this.facade.load();
  }
}
