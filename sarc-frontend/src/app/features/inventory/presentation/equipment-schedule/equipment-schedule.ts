import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

import {
  DAY_LABELS,
  Schedule,
  SCHEDULE_MODE_LABELS,
  shortTime,
  sortIntervals,
} from '../../domain/schedule';

/** Horario del equipo: modo, intervalos semanales (incluidos los nocturnos) y excepciones por fecha. */
@Component({
  selector: 'sarc-equipment-schedule',
  imports: [DatePipe],
  template: `
    <p class="mode">{{ modeLabels[schedule().mode] }}</p>

    @if (schedule().mode === 'WEEKLY') {
      <ul class="list">
        @for (interval of intervals(); track $index) {
          <li>
            <span class="day">{{ dayLabels[interval.day] }}</span>
            <span
              >{{ time(interval.opensAt) }} – {{ time(interval.closesAt) }}
              @if (interval.overnight) {
                <span class="muted">(termina al día siguiente)</span>
              }
            </span>
          </li>
        }
      </ul>
    }

    @if (schedule().exceptions.length > 0) {
      <h3>Excepciones</h3>
      <ul class="list">
        @for (exception of schedule().exceptions; track exception.date) {
          <li>
            <span class="day">{{ exception.date | date: 'mediumDate' }}</span>
            <span>
              {{
                exception.closed
                  ? 'Cerrado'
                  : time(exception.opensAt) + ' – ' + time(exception.closesAt)
              }}
              @if (exception.note) {
                <span class="muted">· {{ exception.note }}</span>
              }
            </span>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .mode {
      margin: 0 0 var(--sarc-space-2);
      font: var(--mat-sys-title-medium);
    }

    h3 {
      margin: var(--sarc-space-4) 0 var(--sarc-space-2);
      font: var(--mat-sys-title-small);
    }

    .list {
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        flex-wrap: wrap;
        gap: var(--sarc-space-1) var(--sarc-space-3);
        padding: var(--sarc-space-1) 0;
      }
    }

    .day {
      min-width: 110px;
      font-weight: 600;
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class EquipmentSchedule {
  readonly schedule = input.required<Schedule>();

  protected readonly intervals = computed(() => sortIntervals(this.schedule().intervals));
  protected readonly modeLabels = SCHEDULE_MODE_LABELS;
  protected readonly dayLabels = DAY_LABELS;
  protected readonly time = shortTime;
}
