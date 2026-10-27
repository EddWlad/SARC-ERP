import { DatePipe, DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { LucideDownload } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { IssuedReportsFacade } from '../../application/issued-reports-facade';
import {
  IssuedReport,
  KIND_LABELS,
  kindLabel,
  REPORT_KINDS,
  ReportKind,
} from '../../domain/issued-report';

/**
 * Reportes emitidos (RF-RPT-08): que se entrego, con que alcance y huella, y la copia exacta para
 * descargarla otra vez.
 */
@Component({
  selector: 'sarc-issued-reports-page',
  imports: [
    DatePipe,
    NgTemplateOutlet,
    MatButtonModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    LucideDownload,
  ],
  providers: [IssuedReportsFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './issued-reports-page.html',
  styleUrl: './issued-reports-page.scss',
})
export class IssuedReportsPage {
  private readonly notifier = inject(Notifier);
  private readonly document = inject(DOCUMENT);

  protected readonly facade = inject(IssuedReportsFacade);
  protected readonly isHandset = injectIsHandset();
  protected readonly quantities = inject(QuantityFormat);
  protected readonly kinds = REPORT_KINDS;
  protected readonly kindLabels = KIND_LABELS;
  protected readonly kindLabel = kindLabel;
  protected readonly columns = ['issued', 'kind', 'scope', 'file', 'actions'];
  protected readonly pageSizeOptions = [10, 20, 50];
  /** Reporte cuya descarga se esta pidiendo. */
  protected readonly downloading = signal<string | null>(null);

  protected onKind(kind: ReportKind | ''): void {
    this.facade.filterKind(kind);
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: este metodo las tipa.
  protected reportOf(row: IssuedReport): IssuedReport {
    return row;
  }

  protected size(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${this.quantities.count(Math.max(1, Math.round(bytes / 1024)))} KB`
      : `${this.quantities.kwh(bytes / (1024 * 1024))} MB`;
  }

  /** La URL firmada entrega el archivo como adjunto: se descarga sin salir de SARC. */
  protected async download(report: IssuedReport): Promise<void> {
    this.downloading.set(report.id);
    try {
      this.document.location.assign(await this.facade.downloadUrl(report));
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    } finally {
      this.downloading.set(null);
    }
  }
}
