import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideCircleAlert } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { toProblem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../../shared/ui/confirm-dialog/confirm-dialog';
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
  ORIGIN_LABELS,
  OUTCOME_LABELS,
  RowClassification,
} from '../../domain/energy-import';
import { ImportStatusBadge } from '../import-status-badge/import-status-badge';

const TESTABLE: ReadonlySet<RowClassification> = new Set(['NEW', 'MODIFIED', 'PENDING_LINK']);

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
    MatChipsModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    LucideArrowLeft,
    LucideCircleAlert,
    ImportStatusBadge,
  ],
  providers: [ImportBatchFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './import-batch-page.html',
  styleUrl: './import-batch-page.scss',
})
export class ImportBatchPage {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(ImportBatchFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly originLabels = ORIGIN_LABELS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly classificationLabels = CLASSIFICATION_LABELS;
  protected readonly classificationHints = CLASSIFICATION_HINTS;
  protected readonly outcomeLabels = OUTCOME_LABELS;
  protected readonly pageSizeOptions = [25, 50, 100];

  /** Solo las clasificaciones que tienen filas en este lote. */
  protected readonly filters = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? []
      : CLASSIFICATIONS.filter((classification) => classificationCount(batch, classification) > 0);
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
  protected readonly canDiscard = computed(() => {
    const batch = this.facade.batch();
    return (
      batch !== undefined &&
      (batch.status === 'READY' || (batch.status === 'FAILED' && !batch.confirmedAt))
    );
  });
  protected readonly canRetry = computed(() => {
    const batch = this.facade.batch();
    return batch !== undefined && batch.status === 'FAILED' && !!batch.confirmedAt;
  });

  protected count(classification: RowClassification): number {
    const batch = this.facade.batch();
    return batch === undefined ? 0 : classificationCount(batch, classification);
  }

  /** Solo las filas que se incorporarian pueden marcarse como prueba. */
  protected canMarkTest(row: ImportRow): boolean {
    return TESTABLE.has(row.classification);
  }

  protected energyOf(row: ImportRow): string {
    return row.energyKwh === null || row.energyKwh === undefined
      ? '—'
      : this.quantities.kwh(row.energyKwh);
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: este metodo las tipa.
  protected rowOf(row: ImportRow): ImportRow {
    return row;
  }

  protected onFilter(classification: RowClassification | '' | undefined): void {
    this.facade.filterBy(classification ?? '');
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async decide(
    rows: readonly ImportRow[],
    decision: { test?: boolean; approved?: boolean },
  ) {
    try {
      await this.facade.decide(rows, decision);
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    }
  }

  protected async confirm(): Promise<void> {
    const batch = this.facade.batch();
    if (batch === undefined) {
      return;
    }
    const confirmed = await this.ask({
      title: 'Confirmar importación',
      message:
        'Se incorporarán las sesiones nuevas y las pendientes de vincular; las modificadas y las de ' +
        'fuente no preferente solo si las aprobó. Las inválidas, duplicadas e idénticas no se tocan.',
      confirmLabel: 'Confirmar',
    });
    if (confirmed) {
      await this.run(() => this.facade.confirm(), 'Incorporando las sesiones...');
    }
  }

  protected async retry(): Promise<void> {
    await this.run(() => this.facade.retry(), 'Reanudando la importación...');
  }

  protected async discard(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Descartar importación',
      message: 'El lote se cierra sin incorporar ninguna sesión. El archivo original se conserva.',
      confirmLabel: 'Descartar',
    });
    if (confirmed) {
      await this.run(() => this.facade.discard(), 'Importación descartada.');
    }
  }

  private async ask(data: ConfirmDialogData): Promise<boolean> {
    return (await firstValueFrom(this.dialog.open(ConfirmDialog, { data }).afterClosed())) === true;
  }

  private async run(operation: () => Promise<unknown>, success: string): Promise<void> {
    try {
      await operation();
      this.notifier.success(success);
    } catch (error) {
      const problem = toProblem(error);
      this.notifier.error(problem.detail);
      if (problem.code === 'CONCURRENT_MODIFICATION') {
        this.facade.reload();
      }
    }
  }
}
