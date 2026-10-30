import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft } from '@lucide/angular';

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
import { NETWORK_LABELS } from '../../../../shared/vocabulary/charging-infrastructure';
import { ImportBatchFacade } from '../../application/import-batch-facade';
import { SOURCE_LABELS } from '../../domain/consumption';
import {
  CLASSIFICATION_HINTS,
  CLASSIFICATION_LABELS,
  classificationCount,
  CLASSIFICATIONS,
  ImportRow,
  OUTCOME_LABELS,
  RowClassification,
  RowDecision,
} from '../../domain/energy-import';

const TESTABLE: ReadonlySet<RowClassification> = new Set([
  'NEW',
  'MODIFIED',
  'EQUIPMENT_CHANGED',
  'LINKABLE',
  'PENDING_LINK',
]);

/**
 * Vista previa de un lote (PRD 3.1): conteos por clasificacion, filas con sus observaciones,
 * decisiones del analista (pruebas, correcciones aprobadas) y confirmacion. Mientras el backend
 * trabaja, la pantalla se actualiza sola.
 */
@Component({
  selector: 'sarc-import-batch-page',
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
  providers: [ImportBatchFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './import-batch-page.html',
  styleUrl: './import-batch-page.scss',
})
export class ImportBatchPage {
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(ImportBatchFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly classificationLabels = CLASSIFICATION_LABELS;
  protected readonly classificationHints = CLASSIFICATION_HINTS;
  protected readonly outcomeLabels = OUTCOME_LABELS;
  protected readonly pageSizeOptions = [25, 50, 100];

  protected readonly classificationOptions = computed<ClassificationOption[]>(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? []
      : CLASSIFICATIONS.map((classification) => ({
          value: classification,
          label: CLASSIFICATION_LABELS[classification],
          count: classificationCount(batch, classification),
          hint: CLASSIFICATION_HINTS[classification],
        }));
  });
  protected readonly completedMessage = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? ''
      : `Importación completada: ${this.quantities.amount(batch.appliedRows, 'sesión aplicada', 'sesiones aplicadas')} ` +
          `(nuevas, corregidas, reasignadas o vinculadas) de ${this.quantities.amount(batch.totalRows, 'fila', 'filas')}.`;
  });
  protected readonly columns = computed(() => [
    'row',
    'session',
    'energy',
    'equipment',
    'classification',
    this.facade.reviewable() ? 'decision' : 'outcome',
  ]);
  /** Filas visibles que aun esperan aprobacion, para aprobarlas de una vez. */
  protected readonly pendingApproval = computed(() =>
    this.facade.rows().filter((row) => row.requiresApproval && !row.approved),
  );
  /** Filas visibles que cambian de cargador y aun no se reasignan. */
  protected readonly pendingReassign = computed(() =>
    this.facade
      .rows()
      .filter((row) => row.classification === 'EQUIPMENT_CHANGED' && !row.reassigned),
  );
  /** Solo las filas que se incorporarian pueden marcarse como prueba. */
  protected canMarkTest(row: ImportRow): boolean {
    return TESTABLE.has(row.classification);
  }

  protected energyOf(row: ImportRow): string {
    return row.classification === 'INVALID' ? '—' : this.quantities.reportedKwh(row.energyKwh);
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: este metodo las tipa.
  protected rowOf(row: ImportRow): ImportRow {
    return row;
  }

  protected onFilter(classification: string): void {
    this.facade.filterBy(classification as RowClassification | '');
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async decide(rows: readonly ImportRow[], decision: RowDecision) {
    try {
      await this.facade.decide(rows, decision);
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    }
  }
}
