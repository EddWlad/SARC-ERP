import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { toProblem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ImportBatchHeader } from '../../../../shared/imports/import-batch-header/import-batch-header';
import {
  ClassificationOption,
  ImportClassificationFilter,
} from '../../../../shared/imports/import-classification-filter';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { UptimeImportBatchFacade } from '../../application/uptime-import-batch-facade';
import {
  UPTIME_CLASSIFICATION_HINTS,
  UPTIME_CLASSIFICATION_LABELS,
  UPTIME_CLASSIFICATIONS,
  UPTIME_OUTCOME_LABELS,
  uptimeClassificationCount,
  uptimeConfirmSummary,
  UptimeImportRow,
  UptimeRowClassification,
  UptimeRowDecision,
} from '../../domain/uptime-import';
import {
  OBSERVATION_LABELS,
  ObservationCode,
  PERIOD_TYPE_LABELS,
} from '../../domain/uptime-labels';
import {
  JustificationData,
  JustificationDialog,
} from '../justification-dialog/justification-dialog';

/** Vista previa de un lote de uptime: conteos, filas, decisiones y confirmacion. */
@Component({
  selector: 'sarc-uptime-import-batch-page',
  imports: [
    DatePipe,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    LucideArrowLeft,
    ImportBatchHeader,
    ImportClassificationFilter,
  ],
  providers: [
    UptimeImportBatchFacade,
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
  ],
  template: `
    <a matButton routerLink="/uptime/importaciones" class="back">
      <svg lucideArrowLeft [size]="18"></svg>
      Importaciones
    </a>

    @if (facade.notFound()) {
      <div class="state" role="alert"><p>El lote de importación no existe.</p></div>
    } @else if (facade.failed()) {
      <div class="state" role="alert">
        <p>No se pudo cargar el lote.</p>
        <button matButton="tonal" type="button" (click)="facade.reload()">Reintentar ahora</button>
      </div>
    } @else if (facade.batch(); as batch) {
      <sarc-import-batch-header
        [state]="facade"
        [kind]="periodLabels[batch.declaredPeriodType]"
        [confirmSummary]="confirmSummary()"
        applyingMessage="Incorporando las mediciones..."
        [completedMessage]="completedMessage()"
      />

      @if (facade.reviewable()) {
        <mat-checkbox
          class="exact"
          [checked]="facade.exactConfirmed()"
          (change)="facade.exactConfirmed.set($event.checked)"
        >
          Confirmo que es una semana exacta. Solo aplica a filas de lunes a lunes sin observaciones.
        </mat-checkbox>
      }

      @if (batch.status !== 'PARSING' && batch.totalRows > 0) {
        <sarc-import-classification-filter
          [options]="classificationOptions()"
          [total]="batch.totalRows"
          [selected]="facade.classification()"
          (selectedChange)="onFilter($event)"
        />

        <section
          class="results"
          aria-label="Filas del lote"
          [attr.aria-busy]="facade.rowsLoading()"
        >
          @if (facade.rowsLoading()) {
            <mat-progress-bar mode="indeterminate" aria-label="Cargando filas" />
          }
          @if (isHandset()) {
            <ul class="cards">
              @for (row of facade.rows(); track row.id) {
                <li class="card">
                  <div class="card-head">
                    <span>
                      <strong>{{ rowLabel(row) }}</strong>
                      <ng-container *ngTemplateOutlet="identity; context: { $implicit: row }" />
                    </span>
                    <ng-container *ngTemplateOutlet="classification; context: { $implicit: row }" />
                  </div>
                  <ng-container *ngTemplateOutlet="readings; context: { $implicit: row }" />
                  <ng-container *ngTemplateOutlet="notes; context: { $implicit: row }" />
                  <ng-container *ngTemplateOutlet="decision; context: { $implicit: row }" />
                </li>
              }
            </ul>
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="facade.rows()" class="table">
                <ng-container matColumnDef="row">
                  <th mat-header-cell *matHeaderCellDef>Fila</th>
                  <td mat-cell *matCellDef="let row">{{ rowLabel(row) }}</td>
                </ng-container>
                <ng-container matColumnDef="connector">
                  <th mat-header-cell *matHeaderCellDef>Conector</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="identity; context: { $implicit: row }" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="readings">
                  <th mat-header-cell *matHeaderCellDef>Lectura</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="readings; context: { $implicit: row }" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="classification">
                  <th mat-header-cell *matHeaderCellDef>Clasificación</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="classification; context: { $implicit: row }" />
                    <ng-container *ngTemplateOutlet="notes; context: { $implicit: row }" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="decision">
                  <th mat-header-cell *matHeaderCellDef>Decisión</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="decision; context: { $implicit: row }" />
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns"></tr>
              </table>
            </div>
          }
          <mat-paginator
            [length]="facade.rowsTotal()"
            [pageIndex]="facade.pageIndex()"
            [pageSize]="facade.pageSize()"
            [pageSizeOptions]="pageSizeOptions"
            (page)="onPage($event)"
            aria-label="Paginación de filas"
          />
        </section>

        <!-- Piezas de una fila, compartidas por la tabla y las tarjetas. -->
        <ng-template #identity let-row>
          <span class="muted block">
            @if (row.sourceSite) {
              {{ row.sourceSite }} ·
            }
            {{ row.sourceCharger }} · {{ row.sourceConnector }}
          </span>
        </ng-template>

        <ng-template #classification let-row>
          <span class="badge" [class]="badgeOf(row)">
            {{ classificationLabels[labelKey(row)] }}
          </span>
        </ng-template>

        <ng-template #readings let-row>
          @if (row.periodStart) {
            <span class="muted block">
              {{ row.periodStart | date: 'short' }} a {{ row.periodEnd | date: 'short' }}
            </span>
          }
          @if (row.classification !== 'INVALID') {
            <span class="muted block">
              @if (row.uptimeBasis === 'REPORTED_PERCENT') {
                Uptime informado {{ quantities.percentage(row.reportedPercent) }} ·
              }
              Disponible {{ minutes(row.availableMinutes) }} ·
              @if (row.uptimeBasis !== 'REPORTED_PERCENT') {
                Ocupado {{ minutes(row.occupiedMinutes) }} ·
              }
              Con falla {{ minutes(row.faultMinutes) }} · Fuera de línea
              {{ minutes(row.offlineMinutes) }}
            </span>
          }
        </ng-template>

        <ng-template #notes let-row>
          @for (code of row.observations; track code) {
            <span class="issue">{{ observationLabel(code) }}</span>
          }
          @for (issue of row.issues; track issue.code) {
            <span class="issue">{{ issue.message }}</span>
          }
          @if (row.justification) {
            <span class="muted block">Justificación: {{ row.justification }}</span>
          }
        </ng-template>

        <ng-template #decision let-row>
          @if (facade.reviewable() && row.classification === 'CONFLICT') {
            <button matButton="tonal" type="button" (click)="justify(row)">
              {{ row.approved ? 'Cambiar justificación' : 'Incluir con justificación' }}
            </button>
          } @else if (facade.reviewable() && row.classification === 'NEW') {
            <mat-checkbox
              [checked]="row.approved"
              (change)="decide(row, { approved: $event.checked })"
            >
              Incluir
            </mat-checkbox>
          } @else if (row.outcome) {
            <span class="muted">{{ outcomeOf(row) }}</span>
          }
        </ng-template>
      }
    }
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/imports/import-history';

    :host {
      display: block;
    }

    .back {
      margin-bottom: var(--sarc-space-3);
    }

    .exact {
      display: block;
      margin-block: var(--sarc-space-3);
    }

    .block {
      overflow-wrap: anywhere;
    }

    .table-scroll {
      overflow-x: auto;
    }

    .issue {
      display: block;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class UptimeImportBatchPage {
  private readonly notifier = inject(Notifier);
  private readonly dialog = inject(MatDialog);

  protected readonly facade = inject(UptimeImportBatchFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly periodLabels = PERIOD_TYPE_LABELS;
  protected readonly classificationLabels = UPTIME_CLASSIFICATION_LABELS;
  protected readonly pageSizeOptions = [25, 50, 100];
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['row', 'connector', 'readings', 'classification', 'decision'];

  protected readonly classificationOptions = computed<ClassificationOption[]>(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? []
      : UPTIME_CLASSIFICATIONS.map((classification) => ({
          value: classification,
          label: UPTIME_CLASSIFICATION_LABELS[classification],
          count: uptimeClassificationCount(batch, classification),
          hint: UPTIME_CLASSIFICATION_HINTS[classification],
        }));
  });
  protected readonly confirmSummary = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined ? undefined : uptimeConfirmSummary(batch);
  });
  protected readonly completedMessage = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? ''
      : `Importación completada: ${this.quantities.amount(batch.appliedRows, 'medición aplicada', 'mediciones aplicadas')} ` +
          `de ${this.quantities.amount(batch.totalRows, 'fila', 'filas')}. Siguen pendientes de validar.`;
  });

  protected labelKey(row: UptimeImportRow): UptimeRowClassification {
    return row.classification;
  }

  protected outcomeOf(row: UptimeImportRow): string {
    return row.outcome ? UPTIME_OUTCOME_LABELS[row.outcome] : '';
  }

  protected badgeOf(row: UptimeImportRow): string {
    switch (row.classification) {
      case 'NEW':
        return 'badge badge-accent';
      case 'CONFLICT':
        return 'badge badge-info';
      case 'INVALID':
      case 'PENDING_LINK':
        return 'badge badge-warn';
      default:
        return 'badge badge-off';
    }
  }

  /** En el informe semanal la fila se ubica por su hoja; el pegado y el CSV por su numero. */
  protected rowLabel(row: UptimeImportRow): string {
    return row.sourceSheet ? `${row.sourceSheet} · fila ${row.sourceRow}` : `Fila ${row.rowNumber}`;
  }

  protected minutes(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : `${this.quantities.measure(value)} min`;
  }

  protected observationLabel(code: string): string {
    return OBSERVATION_LABELS[code as ObservationCode];
  }

  protected onFilter(classification: string): void {
    this.facade.filterBy(classification as UptimeRowClassification | '');
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async justify(row: UptimeImportRow): Promise<void> {
    const justification = await firstValueFrom(
      this.dialog
        .open<JustificationDialog, JustificationData, string>(JustificationDialog, {
          data: {
            title: 'Reemplazar la medición existente',
            action: 'Incluir',
            required: true,
          },
        })
        .afterClosed(),
    );
    if (justification) {
      await this.decide(row, { approved: true, justification });
    }
  }

  protected async decide(row: UptimeImportRow, decision: UptimeRowDecision): Promise<void> {
    try {
      await this.facade.decide([row], decision);
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    }
  }
}
