import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AvailabilityFacade } from '../../application/availability-facade';
import {
  availabilityText,
  coverageText,
  EXCLUSION_LABELS,
} from '../../domain/intermittency-availability';
import { hoursText } from '../../domain/intermittency-summary';
import { formatDay } from '../../domain/intermittency-time';

/**
 * Seccion propia «Disponibilidad estimada (prueba)» del tablero (FR-046a): KPI ponderado con su cobertura, tabla
 * por punto de menor a mayor con «Sin datos» al final, exclusiones, dias sin cubrir y limitaciones. Solo se monta
 * para quien tiene el permiso de disponibilidad. No se presenta como uptime ni como medicion de Enerlink.
 */
@Component({
  selector: 'sarc-availability-section',
  imports: [MatButtonModule, MatProgressBarModule],
  providers: [AvailabilityFacade],
  template: `
    <section
      class="availability"
      aria-labelledby="availability-title"
      [attr.aria-busy]="facade.loading()"
    >
      <h2 id="availability-title">Disponibilidad estimada (prueba)</h2>
      <p class="note">
        Estimación con los días revisados como cobertura. No es uptime ni una medición de Enerlink.
        Solo la ven Analistas y Administradores durante la prueba.
      </p>

      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Calculando la disponibilidad estimada" />
      }

      @if (facade.error(); as message) {
        <div class="state" role="alert">
          <p>{{ message }}</p>
          <button matButton="tonal" type="button" (click)="facade.reload()">Reintentar</button>
        </div>
      } @else if (facade.data(); as data) {
        <ul class="kpi-grid">
          <li class="kpi-tile">
            <span class="kpi-value">{{ percent(data.aggregate.percent) }}</span>
            <span class="kpi-label">Disponibilidad estimada por intermitencias (prueba)</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{
              coverage(data.coverage.coveredDays, data.coverage.daysInScope)
            }}</span>
            <span class="kpi-label">Cobertura: días revisados</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ hours(data.aggregate.evaluatedMinutes) }}</span>
            <span class="kpi-label">Tiempo evaluado</span>
          </li>
          <li class="kpi-tile">
            <span class="kpi-value">{{ hours(data.aggregate.offlineMinutes) }}</span>
            <span class="kpi-label">Fuera de línea confirmado</span>
          </li>
        </ul>

        @if (data.coverage.coveredDays === 0) {
          <p class="note" role="status">
            Sin datos: ningún día del período está marcado como revisado y sin filas pendientes. Un
            día sin marca nunca cuenta como 100 %.
          </p>
        }

        @if (data.blockedDays.length > 0) {
          <div class="panel">
            <h3>Días sin cubrir por filas por vincular</h3>
            <ul class="rows">
              @for (day of data.blockedDays; track day.day) {
                <li class="metric-row">
                  <span>{{ formatDay(day.day) }}</span>
                  <span class="quantity"
                    >{{ day.pendingRows }} {{ day.pendingRows === 1 ? 'fila' : 'filas' }}</span
                  >
                </li>
              }
            </ul>
            <p class="muted">Se recuperan al vincular, omitir o anular esas filas en Revisión.</p>
          </div>
        }

        <div class="panel">
          <h3>Disponibilidad por cargador (de menor a mayor)</h3>
          @if (data.points.length === 0) {
            <p class="muted">No hay cargadores en el alcance de la estimación.</p>
          } @else {
            <ul class="points">
              @for (point of data.points; track point.equipmentId) {
                <li class="point">
                  <div class="who">
                    <strong>{{ point.name }}</strong>
                    @if (point.code) {
                      <span class="muted">{{ point.code }}</span>
                    }
                  </div>
                  <strong class="percent">{{ percent(point.percent) }}</strong>
                  <span class="muted detail">
                    Evaluado {{ hours(point.evaluatedMinutes) }} · Fuera de línea
                    {{ hours(point.offlineMinutes) }} · {{ point.coveredDays }} de
                    {{ data.coverage.daysInScope }} días cubiertos
                  </span>
                </li>
              }
            </ul>
          }
        </div>

        @if (data.exclusions.length > 0) {
          <div class="panel">
            <h3>Fuera del alcance de la estimación</h3>
            <ul class="rows">
              @for (exclusion of data.exclusions; track exclusion.equipmentId) {
                <li class="metric-row">
                  <span>
                    {{ exclusion.name }}
                    @if (exclusion.code) {
                      <span class="muted">({{ exclusion.code }})</span>
                    }
                  </span>
                  <span class="muted">{{ reasons[exclusion.reason] }}</span>
                </li>
              }
            </ul>
          </div>
        }

        <div class="panel">
          <h3>Origen y limitaciones</h3>
          <p class="muted">Origen: {{ data.labels.origin }}.</p>
          <ul class="limits">
            @for (limitation of data.limitations; track limitation) {
              <li class="muted">{{ limitation }}</li>
            }
          </ul>
        </div>
      }
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/dashboard';

    :host {
      display: block;
    }

    .availability {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-4);

      h2 {
        margin: 0;
        font: var(--mat-sys-title-medium);
      }

      h3 {
        margin: 0 0 var(--sarc-space-3);
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-title-small);
      }

      p {
        margin: 0;
      }
    }

    .note,
    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }

    .rows,
    .points,
    .limits {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .limits {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-1);
    }

    .point {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: var(--sarc-space-1) var(--sarc-space-3);
      padding: var(--sarc-space-2) 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }

    .who {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: var(--sarc-space-1) var(--sarc-space-2);
      overflow-wrap: anywhere;
    }

    .percent {
      font: var(--mat-sys-title-medium);
      text-align: end;
    }

    .detail {
      grid-column: 1 / -1;
    }

    .quantity {
      flex: none;
      text-align: end;
    }
  `,
})
export class AvailabilitySection {
  protected readonly facade = inject(AvailabilityFacade);
  protected readonly reasons = EXCLUSION_LABELS;
  protected readonly percent = availabilityText;
  protected readonly coverage = coverageText;
  protected readonly hours = hoursText;
  protected readonly formatDay = formatDay;
}
