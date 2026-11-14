import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucidePencil, LucideListChecks } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ImportBatchHeader } from '../../../../shared/imports/import-batch-header/import-batch-header';
import {
  ClassificationOption,
  ImportClassificationFilter,
} from '../../../../shared/imports/import-classification-filter';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { IntermittencyImportBatchFacade } from '../../application/import-batch-facade';
import { ImportRowDialogData } from '../../application/import-row-actions';
import {
  blockingRows,
  classificationCount,
  IMPORT_CLASSIFICATION_HINTS,
  IMPORT_CLASSIFICATIONS,
  importConfirmSummary,
  IntermittencyImportRow,
  ROW_DECISION_LABELS,
  RowDecision,
  RowWindow,
  WINDOW_RESOLUTION_LABELS,
  WindowResolution,
} from '../../domain/intermittency-import';
import {
  CLASSIFICATION_LABELS,
  issueText,
  RowClassification,
} from '../../domain/intermittency-labels';
import {
  durationText,
  formatDay,
  formatInstant,
  present,
  sourceCellText,
} from '../../domain/intermittency-time';
import { ImportRowEditor } from '../import-row-editor/import-row-editor';
import { RowDecisionPanel } from '../row-decision-panel/row-decision-panel';

/**
 * Vista previa de un lote de intermitencias: cada fila con su archivo, fila, ventanas, valor original,
 * interpretacion, clasificacion y motivo (FR-023). Las filas se corrigen y se deciden aqui; el lote se confirma
 * cuando no queda nada por resolver.
 */
@Component({
  selector: 'sarc-intermittency-import-batch-page',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    LucideArrowLeft,
    LucideListChecks,
    LucidePencil,
    ImportBatchHeader,
    ImportClassificationFilter,
  ],
  providers: [
    IntermittencyImportBatchFacade,
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
  ],
  template: `
    <a matButton routerLink="/intermitencias/importaciones" class="back">
      <svg lucideArrowLeft [size]="18" aria-hidden="true"></svg>
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
        kind="Intermitencias"
        [confirmSummary]="confirmSummary()"
        applyingMessage="Creando las ventanas..."
        [completedMessage]="completedMessage()"
      />

      @if (facade.reviewable() && blocking() > 0) {
        <p class="blocking" role="status">
          Quedan {{ quantities.amount(blocking(), 'fila', 'filas') }} por resolver: corrija los
          datos, vincule el punto, decida u omita la fila para poder confirmar.
        </p>
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
                      <strong>Fila {{ row.rowNumber }}</strong>
                      <ng-container *ngTemplateOutlet="identity; context: { $implicit: row }" />
                    </span>
                    <ng-container *ngTemplateOutlet="classification; context: { $implicit: row }" />
                  </div>
                  <ng-container *ngTemplateOutlet="windows; context: { $implicit: row }" />
                  <ng-container *ngTemplateOutlet="actions; context: { $implicit: row }" />
                </li>
              }
            </ul>
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="facade.rows()" class="table">
                <ng-container matColumnDef="row">
                  <th mat-header-cell *matHeaderCellDef>Fila</th>
                  <td mat-cell *matCellDef="let row">
                    <strong>{{ row.rowNumber }}</strong>
                    <ng-container *ngTemplateOutlet="identity; context: { $implicit: row }" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="windows">
                  <th mat-header-cell *matHeaderCellDef>Ventanas</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="windows; context: { $implicit: row }" />
                  </td>
                </ng-container>
                <ng-container matColumnDef="classification">
                  <th mat-header-cell *matHeaderCellDef>Clasificación</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="classification; context: { $implicit: row }" />
                    @for (issue of row.interpretation.rowIssues; track issue.code) {
                      <span class="issue" [class.info]="issue.code === 'NAME_DIFFERS'">
                        {{ issueText(issue) }}
                      </span>
                    }
                  </td>
                </ng-container>
                <ng-container matColumnDef="decision">
                  <th mat-header-cell *matHeaderCellDef>Decisión</th>
                  <td mat-cell *matCellDef="let row">
                    <ng-container *ngTemplateOutlet="actions; context: { $implicit: row }" />
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
            @if (row.interpretation.date) {
              {{ formatDay(row.interpretation.date) }} ·
            }
            {{ row.interpretation.code || 'Sin código' }}
            @if (row.interpretation.name) {
              · {{ row.interpretation.name }}
            }
          </span>
        </ng-template>

        <ng-template #classification let-row>
          <span class="badge" [class]="badgeOf(row.classification)">
            {{ classificationLabel(row.classification) }}
          </span>
        </ng-template>

        <ng-template #windows let-row>
          <ul class="window-list">
            @for (window of row.interpretation.windows; track window.pair) {
              <li [class.ignored]="present(window.mergedIntoPair) || window.decision === 'OMIT'">
                <span class="pair">Par {{ window.pair }}</span>
                <span class="original">
                  Original: {{ sourceCellText(window.startText) }} →
                  {{ sourceCellText(window.endText) }}
                </span>
                <span class="interpreted">{{ windowText(window) }}</span>
                @for (issue of window.issues; track issue.code) {
                  <span class="issue">{{ issueText(issue) }}</span>
                }
                @if (window.test) {
                  <span class="badge badge-off">Prueba</span>
                }
                @if (window.decision) {
                  <span class="badge badge-info">{{ resolutionLabel(window.decision) }}</span>
                }
                @if (present(window.mergedIntoPair)) {
                  <span class="muted">Fusionada en el par {{ window.mergedIntoPair }}</span>
                }
              </li>
            }
          </ul>
          @if (present(row.interpretation.fileTotalMinutes)) {
            <span class="muted block">
              Total del archivo {{ durationText(row.interpretation.fileTotalMinutes) }} ·
              recalculado
              {{ durationText(row.interpretation.computedTotalMinutes) }}
              @if (present(row.interpretation.computedWithDuplicatesMinutes)) {
                (con el duplicado
                {{ durationText(row.interpretation.computedWithDuplicatesMinutes) }})
              }
            </span>
          }
        </ng-template>

        <ng-template #actions let-row>
          @if (row.decision) {
            <span class="muted block">{{ decisionLabel(row.decision) }}</span>
            @if (row.skipReason) {
              <span class="muted block">Motivo: {{ row.skipReason }}</span>
            }
          }
          @if (facade.reviewable()) {
            <div class="decision">
              <button matButton="tonal" type="button" (click)="correct(row)">
                <svg lucidePencil [size]="18" aria-hidden="true"></svg>
                Corregir
              </button>
              <button matButton="outlined" type="button" (click)="resolve(row)">
                <svg lucideListChecks [size]="18" aria-hidden="true"></svg>
                Resolver
              </button>
            </div>
          }
        </ng-template>
      }
    }
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/imports/import-history';
    @use '../../../../shared/imports/import-rows';

    :host {
      display: block;
    }

    .blocking {
      margin: 0 0 var(--sarc-space-3);
      color: var(--mat-sys-on-surface-variant);
    }

    .block {
      overflow-wrap: anywhere;
    }

    .window-list {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: var(--sarc-space-1) var(--sarc-space-3);

        &.ignored {
          opacity: 0.6;
        }
      }
    }

    .pair {
      font: var(--mat-sys-label-large);
    }

    .original {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .issue {
      color: var(--mat-sys-error);
    }

    // Aviso que no bloquea: la fila se vincula por el código.
    .issue.info {
      color: var(--mat-sys-on-surface-variant);
    }

    // Corregir y Resolver apilados, del mismo ancho y separados; en la tarjeta del teléfono, lado a
    // lado y a todo el ancho.
    .decision {
      display: inline-grid;
      gap: var(--sarc-space-2);
    }

    // Una fila tiene varias ventanas: fila, clasificación y decisión empiezan arriba, junto al Par 1.
    td.mat-mdc-cell {
      vertical-align: top;
      padding-block: var(--sarc-space-3);
    }

    .card .decision {
      display: grid;
      grid-template-columns: 1fr 1fr;
    }
  `,
})
export class IntermittencyImportBatchPage {
  private readonly dialog = inject(MatDialog);

  protected readonly facade = inject(IntermittencyImportBatchFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly formatDay = formatDay;
  protected readonly present = present;
  protected readonly issueText = issueText;
  protected readonly sourceCellText = sourceCellText;
  protected readonly durationText = durationText;
  protected readonly pageSizeOptions = [25, 50, 100];
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['row', 'windows', 'classification', 'decision'];

  protected readonly classificationOptions = computed<ClassificationOption[]>(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? []
      : IMPORT_CLASSIFICATIONS.map((classification) => ({
          value: classification,
          label: CLASSIFICATION_LABELS[classification],
          count: classificationCount(batch, classification),
          hint: IMPORT_CLASSIFICATION_HINTS[classification],
        }));
  });
  protected readonly confirmSummary = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined ? undefined : importConfirmSummary(batch);
  });
  protected readonly blocking = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined ? 0 : blockingRows(batch);
  });
  protected readonly completedMessage = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? ''
      : `Importación completada: ${this.quantities.amount(batch.appliedRows, 'fila incorporada', 'filas incorporadas')} ` +
          `de ${this.quantities.amount(batch.totalRows, 'fila', 'filas')}.`;
  });

  protected classificationLabel(classification: RowClassification): string {
    return CLASSIFICATION_LABELS[classification];
  }

  protected resolutionLabel(resolution: WindowResolution): string {
    return WINDOW_RESOLUTION_LABELS[resolution];
  }

  protected decisionLabel(decision: RowDecision): string {
    return ROW_DECISION_LABELS[decision];
  }

  protected badgeOf(classification: RowClassification): string {
    switch (classification) {
      case 'NEW':
        return 'badge badge-accent';
      case 'IDENTICAL':
        return 'badge badge-off';
      case 'REVIEW':
      case 'CONFLICT':
        return 'badge badge-info';
      default:
        return 'badge badge-warn';
    }
  }

  /** El instante interpretado de la ventana, o su texto original si no se pudo interpretar. */
  protected windowText(window: RowWindow): string {
    if (window.startAt) {
      const end = window.endAt ? formatInstant(window.endAt) : 'abierta';
      const minutes = window.minutes
        ? ` · ${durationText(window.minutes)} (${window.minutes} min)`
        : '';
      return `${formatInstant(window.startAt)} → ${end}${minutes}`;
    }
    return 'Sin interpretar';
  }

  protected onFilter(classification: string): void {
    this.facade.filterBy(classification as RowClassification | '');
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async correct(row: IntermittencyImportRow): Promise<void> {
    await this.openDialog(ImportRowEditor, row);
  }

  protected async resolve(row: IntermittencyImportRow): Promise<void> {
    await this.openDialog(RowDecisionPanel, row);
  }

  private async openDialog(
    component: typeof ImportRowEditor | typeof RowDecisionPanel,
    row: IntermittencyImportRow,
  ): Promise<void> {
    const data: ImportRowDialogData = { row, actions: this.facade };
    await firstValueFrom(
      this.dialog
        .open<unknown, ImportRowDialogData>(component, {
          data,
          width: '720px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    // Cada cambio ya recargo la pagina; aqui solo se cierra el ciclo del dialogo.
  }
}
