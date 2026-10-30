import { NgTemplateOutlet } from '@angular/common';
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
import { EquipmentImportBatchFacade } from '../../application/equipment-import-batch-facade';
import {
  EQUIPMENT_CLASSIFICATION_HINTS,
  EQUIPMENT_CLASSIFICATION_LABELS,
  EQUIPMENT_CLASSIFICATIONS,
  EQUIPMENT_OUTCOME_LABELS,
  equipmentClassificationCount,
  EquipmentImportRow,
  EquipmentRowClassification,
} from '../../domain/equipment-import';

const CONFIRM_MESSAGE =
  'Se registrarán los equipos nuevos, se completarán las identidades Enerlink y se actualizarán ' +
  'los modificados que aprobó, con sus identidades externas verificadas. Los modificados sin ' +
  'aprobar se omiten completos, también su identidad. Las filas inválidas, duplicadas, en ' +
  'conflicto o de equipos eliminados no se tocan.';

/**
 * Vista previa de una carga masiva de equipos (ADR 0007): conteos por clasificacion, filas con sus
 * observaciones, aprobacion de las actualizaciones y confirmacion. Mientras el backend trabaja, la
 * pantalla se actualiza sola.
 */
@Component({
  selector: 'sarc-equipment-import-batch-page',
  imports: [
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
    EquipmentImportBatchFacade,
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
  ],
  templateUrl: './equipment-import-batch-page.html',
  styleUrl: './equipment-import-batch-page.scss',
})
export class EquipmentImportBatchPage {
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(EquipmentImportBatchFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly confirmMessage = CONFIRM_MESSAGE;
  protected readonly classificationLabels = EQUIPMENT_CLASSIFICATION_LABELS;
  protected readonly classificationHints = EQUIPMENT_CLASSIFICATION_HINTS;
  protected readonly outcomeLabels = EQUIPMENT_OUTCOME_LABELS;
  protected readonly pageSizeOptions = [25, 50, 100];

  protected readonly classificationOptions = computed<ClassificationOption[]>(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? []
      : EQUIPMENT_CLASSIFICATIONS.map((classification) => ({
          value: classification,
          label: EQUIPMENT_CLASSIFICATION_LABELS[classification],
          count: equipmentClassificationCount(batch, classification),
          hint: EQUIPMENT_CLASSIFICATION_HINTS[classification],
        }));
  });
  protected readonly completedMessage = computed(() => {
    const batch = this.facade.batch();
    return batch === undefined
      ? ''
      : `Carga completada: ${this.quantities.amount(batch.appliedRows, 'equipo registrado o actualizado', 'equipos registrados o actualizados')} ` +
          `de ${this.quantities.amount(batch.totalRows, 'fila', 'filas')}.`;
  });
  protected readonly columns = computed(() => [
    'row',
    'equipment',
    'connectors',
    'identities',
    'classification',
    this.facade.reviewable() ? 'decision' : 'outcome',
  ]);
  /** Filas visibles que aun esperan aprobacion, para aprobarlas de una vez. */
  protected readonly pendingApproval = computed(() =>
    this.facade.rows().filter((row) => row.requiresApproval && !row.approved),
  );

  /** Red, tipo y marca tal como vienen en la plantilla. */
  protected classificationOf(row: EquipmentImportRow): string {
    return [row.network, row.type, row.brand].filter((value) => !!value).join(' · ');
  }

  /** Identidades externas de la fila, como las vera el analista en el equipo. */
  protected identitiesOf(row: EquipmentImportRow): string[] {
    const identities: string[] = [];
    if (row.enerlinkCharger) {
      identities.push(
        `Enerlink ${row.enerlinkScope ? row.enerlinkScope + ' / ' : ''}${row.enerlinkCharger}`,
      );
    }
    if (row.wallboxId) {
      identities.push(`Wallbox ${row.wallboxId}`);
    }
    if (row.ocppId) {
      identities.push(`OCPP ${row.ocppId}`);
    }
    return identities;
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: este metodo las tipa.
  protected rowOf(row: EquipmentImportRow): EquipmentImportRow {
    return row;
  }

  protected onFilter(classification: string): void {
    this.facade.filterBy(classification as EquipmentRowClassification | '');
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async approve(rows: readonly EquipmentImportRow[], approved: boolean): Promise<void> {
    try {
      await this.facade.decide(rows, { approved });
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    }
  }
}
