import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { LucideArrowLeft, LucideFileSpreadsheet, LucideFileUp } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { Problem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ImportBatchLike, ORIGIN_LABELS } from '../../../../shared/imports/import-batch';
import {
  ImportDialog,
  ImportDialogData,
} from '../../../../shared/imports/import-dialog/import-dialog';
import { ImportStatusBadge } from '../../../../shared/imports/import-status-badge';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { EquipmentImportsFacade } from '../../application/equipment-imports-facade';
import { EquipmentImportBatch } from '../../domain/equipment-import';

/**
 * Carga masiva de equipos (ADR 0007): plantilla para descargar, historial de lotes y punto de
 * entrada para cargar uno nuevo.
 */
@Component({
  selector: 'sarc-equipment-imports-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    LucideArrowLeft,
    LucideFileSpreadsheet,
    LucideFileUp,
    ImportStatusBadge,
  ],
  providers: [
    EquipmentImportsFacade,
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
  ],
  templateUrl: './equipment-imports-page.html',
  styleUrl: './equipment-imports-page.scss',
})
export class EquipmentImportsPage {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(EquipmentImportsFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['created', 'status', 'rows', 'applied'];
  protected readonly pageSizeOptions = [10, 20, 50];
  protected readonly originLabels = ORIGIN_LABELS;

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: este metodo las tipa.
  protected batchOf(row: EquipmentImportBatch): EquipmentImportBatch {
    return row;
  }

  protected async downloadTemplate(): Promise<void> {
    try {
      await this.facade.downloadTemplate();
    } catch (error) {
      this.notifier.error((error as Problem).detail);
    }
  }

  protected async create(): Promise<void> {
    const data: ImportDialogData = {
      fileHint: 'Plantilla de SARC completada (XLSX o CSV)',
      pasteHint: 'Copie desde la hoja Equipos de la plantilla, incluida la fila de encabezados.',
      upload: (file) => this.facade.upload(file),
      paste: (text) => this.facade.paste(text),
    };
    const batch = await firstValueFrom(
      this.dialog
        .open<ImportDialog, ImportDialogData, ImportBatchLike>(ImportDialog, {
          data,
          width: '560px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (batch !== undefined) {
      await this.router.navigate(['/inventario/importaciones', batch.id]);
    }
  }
}
