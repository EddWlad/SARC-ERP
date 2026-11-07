import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import {
  LucideArrowLeft,
  LucideBan,
  LucideCircleCheck,
  LucidePencil,
  LucideRotateCcw,
} from '@lucide/angular';

import { StatesChart } from '../states-chart/states-chart';
import { StateCompositionResponse } from '../../../../core/api/generated';
import { MeasurementFacade } from '../../application/measurement-facade';
import {
  OBSERVATION_LABELS,
  PERIOD_TYPE_LABELS,
  READING_CLASS_LABELS,
  REVIEW_STATE_LABELS,
} from '../../domain/uptime-labels';
import { CorrectionForm } from '../correction-form/correction-form';
import {
  JustificationData,
  JustificationDialog,
} from '../justification-dialog/justification-dialog';
import { QuantityFormat } from '../../../../shared/format/quantity-format';

/** Detalle de una medicion: vigente, revision pendiente, historial y acciones. */
@Component({
  selector: 'sarc-measurement-page',
  imports: [
    DatePipe,
    StatesChart,
    MatButtonModule,
    MatProgressBarModule,
    RouterLink,
    LucideArrowLeft,
    LucideBan,
    LucideCircleCheck,
    LucidePencil,
    LucideRotateCcw,
  ],
  providers: [MeasurementFacade],
  template: `
    <a matButton routerLink="/uptime/revision" class="back">
      <svg lucideArrowLeft [size]="18"></svg>
      Revisión
    </a>

    @if (facade.loading() && !facade.detail()) {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando medición" />
    }
    @if (facade.error(); as error) {
      <div class="state" role="alert">
        <p>{{ error }}</p>
      </div>
    }
    @if (facade.detail(); as item) {
      <header class="page-header">
        <div>
          <h1>{{ item.equipmentName }} · Conector {{ item.ordinal }}</h1>
          <p>
            {{ item.periodType ? periods[item.periodType] : 'Por conciliar' }} ·
            @if (item.temporalPrecision === 'KNOWN_RANGE') {
              {{ item.periodStart | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }} a
              {{ item.periodEnd | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }}
            } @else {
              Horario real no informado
            }
            @if (item.referenceWeek) {
              · Semana de referencia {{ item.referenceWeek }}
            }
          </p>
          @if (labelDiffers()) {
            <p>
              En la fecha del hecho: {{ item.factLabel.equipmentName }} ·
              {{ item.factLabel.connectorType }}
            </p>
          }
          @if (facade.canEdit) {
            <p class="badges">
              <span class="badge" [class.badge-info]="item.reviewState === 'PENDING'">
                Revisión {{ item.revision }}: {{ states[item.reviewState] }}
              </span>
              @if (item.excluded) {
                <span class="badge badge-off">Excluida de los indicadores</span>
              }
            </p>
          }
        </div>
        @if (facade.canEdit) {
          <div class="header-actions">
            @if (
              item.reviewState === 'PENDING' && item.temporalPrecision !== 'REQUIRES_RECONCILIATION'
            ) {
              <button
                matButton="filled"
                type="button"
                [disabled]="facade.busy() || item.validatable === false"
                (click)="validate()"
              >
                <svg lucideCircleCheck [size]="18"></svg>
                Validar
              </button>
            }
            <button
              matButton="outlined"
              type="button"
              [disabled]="facade.busy()"
              (click)="classifyTemporal()"
            >
              Conciliar período
            </button>
            <button
              matButton="tonal"
              type="button"
              [disabled]="facade.busy() || item.temporalPrecision === 'REQUIRES_RECONCILIATION'"
              (click)="correct()"
            >
              <svg lucidePencil [size]="18"></svg>
              Corregir
            </button>
            @if (item.excluded) {
              <button
                matButton="outlined"
                type="button"
                [disabled]="facade.busy()"
                (click)="restore()"
              >
                <svg lucideRotateCcw [size]="18"></svg>
                Restaurar
              </button>
            } @else {
              <button
                matButton="outlined"
                type="button"
                [disabled]="facade.busy()"
                (click)="exclude()"
              >
                <svg lucideBan [size]="18"></svg>
                Excluir
              </button>
            }
          </div>
        }
      </header>

      <ul class="kpi-grid">
        <li class="kpi-tile">
          <span class="kpi-value">{{ quantities.percentage(item.uptimePercent) }}</span>
          <span class="kpi-label">
            {{ item.uptimeBasis === 'REPORTED_PERCENT' ? 'Uptime informado' : 'Uptime' }} ·
            {{ classes[item.readingClass] }}
          </span>
        </li>
        <li class="kpi-tile">
          <span class="kpi-value">
            {{ quantities.measure(item.availableMinutes) }}<span class="kpi-unit">min</span>
          </span>
          <span class="kpi-label">Disponible</span>
        </li>
        @if (item.uptimeBasis !== 'REPORTED_PERCENT') {
          <li class="kpi-tile">
            <span class="kpi-value">
              {{ quantities.measure(item.occupiedMinutes) }}<span class="kpi-unit">min</span>
            </span>
            <span class="kpi-label">Ocupado</span>
          </li>
        }
        <li class="kpi-tile">
          <span class="kpi-value">
            {{ quantities.measure(item.faultMinutes) }}<span class="kpi-unit">min</span>
          </span>
          <span class="kpi-label">Con falla</span>
        </li>
        <li class="kpi-tile">
          <span class="kpi-value">
            {{ quantities.measure(item.offlineMinutes) }}<span class="kpi-unit">min</span>
          </span>
          <span class="kpi-label">Fuera de línea</span>
        </li>
      </ul>

      @if (composition(); as statesComposition) {
        <section class="panel detail">
          <sarc-states-chart [composition]="statesComposition" [level]="2" />
        </section>
      }
      @if (facade.canEdit) {
        @if (pendingOverEffective(); as effective) {
          <p class="dashboard-note detail">
            La revisión {{ effective.revision }} sigue vigente con
            {{ quantities.percentage(effective.uptimePercent) }} hasta validar la corrección.
          </p>
        }
        @for (code of item.observations; track code) {
          <p class="observation">{{ observations[code] }}</p>
        }
      }

      @if (facade.history(); as history) {
        <section class="results detail" aria-labelledby="history-title">
          <h2 class="results-title" id="history-title">Historial</h2>
          <ol class="cards">
            @for (event of history.temporalChanges; track event.eventId) {
              <li class="card">
                <strong
                  >Clasificación temporal ·
                  {{ event.actorKind === 'SYSTEM' ? 'Sistema' : 'Operador' }}</strong
                >
                <span
                  >{{ event.occurredAt | date: 'dd/MM/yyyy HH:mm:ss' : '-0500' }} ·
                  {{ event.outcome }}</span
                >
                <p>{{ event.reason }}</p>
                <p>
                  Antecedente temporal (puede incluir horario asignado):
                  {{ event.previousTemporal['precision'] }} ·
                  {{ event.previousTemporal['start'] }} a {{ event.previousTemporal['end'] }}
                </p>
                <p>
                  Resultado: {{ event.resultTemporal['precision'] }} · Referencia
                  {{ event.resultTemporal['referenceWeek'] }}
                </p>
              </li>
            }
            @for (revision of history.revisions; track revision.revision) {
              <li class="card">
                <div class="card-head">
                  <div>
                    <strong>Revisión {{ revision.revision }}</strong>
                    <span class="muted block">
                      {{ revision.enteredAt | date: 'dd/MM/yyyy HH:mm' : '-0500' }}
                    </span>
                  </div>
                  <span class="history-value">
                    <span class="badge badge-off">{{ states[revision.reviewState] }}</span>
                    <strong>{{ quantities.percentage(revision.uptimePercent) }}</strong>
                  </span>
                </div>
                @if (revision.justification; as justification) {
                  <span class="muted">{{ justification }}</span>
                }
                @if (revision.validationJustification; as justification) {
                  <span class="muted">Validación: {{ justification }}</span>
                }
              </li>
            }
            @for (exclusion of history.exclusions; track exclusion.decidedAt) {
              <li class="card">
                <div class="card-head">
                  <div>
                    <strong>{{
                      exclusion.action === 'EXCLUDED' ? 'Excluida' : 'Restaurada'
                    }}</strong>
                    <span class="muted block">
                      {{ exclusion.decidedAt | date: 'dd/MM/yyyy HH:mm' : '-0500' }}
                    </span>
                  </div>
                </div>
                <span class="muted">{{ exclusion.justification }}</span>
              </li>
            }
          </ol>
        </section>
      }
    }
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/dashboard';
    @use '../../../../shared/ui/badges';

    :host {
      display: block;
    }

    .back {
      margin-bottom: var(--sarc-space-3);
    }

    .badges {
      display: flex;
      flex-wrap: wrap;
      gap: var(--sarc-space-2);
      margin-top: var(--sarc-space-2);
    }

    .detail {
      margin-top: var(--sarc-space-4);
    }

    .observation {
      margin: var(--sarc-space-3) 0 0;
      color: var(--mat-sys-error);
    }

    .results-title {
      margin: 0;
      padding: var(--sarc-space-4) var(--sarc-space-4) var(--sarc-space-2);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-title-small);
    }

    .history-value {
      display: flex;
      align-items: center;
      gap: var(--sarc-space-2);
    }
  `,
})
export class MeasurementPage implements OnInit {
  readonly id = input.required<string>();
  protected readonly facade = inject(MeasurementFacade);
  protected readonly quantities = inject(QuantityFormat);
  private readonly dialog = inject(MatDialog);
  protected readonly periods = PERIOD_TYPE_LABELS;
  protected readonly classes = READING_CLASS_LABELS;
  protected readonly states = REVIEW_STATE_LABELS;
  protected readonly observations = OBSERVATION_LABELS;
  protected readonly labelDiffers = computed(() => {
    const item = this.facade.detail();
    return !!item && item.factLabel.equipmentName !== item.equipmentName;
  });
  protected readonly pendingOverEffective = computed(() => {
    const effective = this.facade.effective();
    return effective && effective.revision !== this.facade.detail()?.revision ? effective : null;
  });

  protected readonly composition = computed<StateCompositionResponse | null>(() => {
    const item = this.facade.detail();
    if (!item || item.readingClass !== 'MEASURED') return null;
    const values = [
      item.availableMinutes,
      item.occupiedMinutes,
      item.faultMinutes,
      item.offlineMinutes,
    ];
    const complete = values.every((value) => value != null);
    return {
      availableMinutes: item.availableMinutes,
      occupiedMinutes: item.occupiedMinutes,
      faultMinutes: item.faultMinutes,
      offlineMinutes: item.offlineMinutes,
      totalKnownMinutes: values.reduce<number>((sum, value) => sum + (value ?? 0), 0),
      selectedMeasurements: 1,
      completeMeasurements: complete ? 1 : 0,
      isComplete: complete,
      knownMeasurementsByState: {
        available: item.availableMinutes == null ? 0 : 1,
        occupied: item.occupiedMinutes == null ? 0 : 1,
        fault: item.faultMinutes == null ? 0 : 1,
        offline: item.offlineMinutes == null ? 0 : 1,
      },
    };
  });

  ngOnInit(): void {
    void this.facade.load(this.id());
  }

  /** Solo una observada validable exige justificacion. */
  protected validate(): void {
    if (!this.facade.detail()?.observations?.length) {
      void this.facade.validate();
      return;
    }
    this.justify(
      { title: 'Validar medición observada', action: 'Validar', required: true },
      (text) => this.facade.validate(text),
    );
  }

  protected classifyTemporal(): void {
    const item = this.facade.detail();
    if (!item) return;
    this.dialog.open(JustificationDialog, {
      data: {
        title: 'Conciliar período',
        action: 'Guardar decisión',
        required: true,
        temporal: { referenceWeek: item.referenceWeek },
        error: () => this.facade.error(),
        submitTemporal: (
          request: Omit<
            import('../../../../core/api/generated').TemporalClassificationRequest,
            'version'
          >,
        ) => this.facade.classifyTemporal(request),
      },
      autoFocus: 'first-tabbable',
    });
  }

  protected correct(): void {
    this.dialog.open(CorrectionForm, { data: this.facade, autoFocus: 'first-tabbable' });
  }

  protected exclude(): void {
    this.justify({ title: 'Excluir medición', action: 'Excluir', required: true }, (text) =>
      this.facade.exclude(text),
    );
  }

  protected restore(): void {
    this.justify({ title: 'Restaurar medición', action: 'Restaurar', required: true }, (text) =>
      this.facade.restore(text),
    );
  }

  private justify(data: JustificationData, action: (text: string) => Promise<boolean>): void {
    this.dialog
      .open(JustificationDialog, { data })
      .afterClosed()
      .subscribe((result?: { text: string }) => {
        if (result) void action(result.text);
      });
  }
}
