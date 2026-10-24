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
import { injectIsHandset } from '../../../../shared/ui/handset';
import { EnergyImportsFacade } from '../../application/energy-imports-facade';
import { SOURCE_LABELS } from '../../domain/consumption';
import { ImportBatch, ORIGIN_LABELS } from '../../domain/energy-import';
import { ImportDialog, ImportDialogData } from '../import-dialog/import-dialog';
import { ImportStatusBadge } from '../import-status-badge/import-status-badge';

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
      upload: (source, file) => this.facade.upload(source, file),
      paste: (source, text) => this.facade.paste(source, text),
    };
    const batch = await firstValueFrom(
      this.dialog
        .open<ImportDialog, ImportDialogData, ImportBatch>(ImportDialog, {
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
