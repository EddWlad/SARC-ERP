import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { LucideFileUp } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ImportBatchLike, ORIGIN_LABELS } from '../../../../shared/imports/import-batch';
import {
  ImportDialog,
  ImportDialogData,
} from '../../../../shared/imports/import-dialog/import-dialog';
import { ImportStatusBadge } from '../../../../shared/imports/import-status-badge';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { EnergyImportsFacade } from '../../application/energy-imports-facade';
import { SessionSource, SOURCE_LABELS, SOURCES } from '../../domain/consumption';
import { ImportBatch, SOURCE_COLUMNS } from '../../domain/energy-import';

/** Historial de importaciones de sesiones y punto de entrada para cargar un lote nuevo. */
@Component({
  selector: 'sarc-imports-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    LucideFileUp,
    ImportStatusBadge,
  ],
  providers: [EnergyImportsFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './imports-page.html',
  styleUrl: './imports-page.scss',
})
export class ImportsPage {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly facade = inject(EnergyImportsFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['created', 'source', 'status', 'rows', 'applied'];
  protected readonly pageSizeOptions = [10, 20, 50];
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly originLabels = ORIGIN_LABELS;

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: este metodo las tipa.
  protected batchOf(row: ImportBatch): ImportBatch {
    return row;
  }

  protected async create(): Promise<void> {
    const data: ImportDialogData = {
      fileHint: 'Reporte original en XLSX, XLS o CSV',
      pasteHint: 'Incluya la fila de encabezados; las columnas extra se ignoran.',
      choice: {
        label: 'Plataforma de origen',
        requiredMessage: 'Seleccione la plataforma de origen',
        options: SOURCES.map((source) => ({
          value: source,
          label: SOURCE_LABELS[source],
          hint: `Columnas esperadas: ${SOURCE_COLUMNS[source]}`,
        })),
      },
      upload: (file, source) => this.facade.upload(source as SessionSource, file),
      paste: (text, source) => this.facade.paste(source as SessionSource, text),
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
      await this.router.navigate(['/consumo/importaciones', batch.id]);
    }
  }
}
