import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject, Injector, LOCALE_ID, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { LucideDownload } from '@lucide/angular';

import { Problem } from '../../../../core/http/problem';
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
  REPORT_TIME_ZONE,
  ReportKind,
} from '../../domain/issued-report';
import { ManagerialReportDialog } from '../managerial-report-dialog/managerial-report-dialog';

/** Motivo del backend para un original que los roles actuales no pueden abrir. */
const RESTRICTED = 'REPORT_OPERATIONAL_CONTENT_RESTRICTED';

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
  private readonly dialog = inject(MatDialog);
  private readonly injector = inject(Injector);
  private readonly cutOffFormat = new Intl.DateTimeFormat(inject(LOCALE_ID), {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: REPORT_TIME_ZONE,
  });

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
  /** Reportes cuya huella SHA-256 la persona pidio ver; por defecto no se muestra. */
  private readonly shownFingerprints = signal<ReadonlySet<string>>(new Set());

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

  protected fingerprintShown(report: IssuedReport): boolean {
    return this.shownFingerprints().has(report.id);
  }

  protected toggleFingerprint(report: IssuedReport): void {
    this.shownFingerprints.update((shown) => {
      const next = new Set(shown);
      if (!next.delete(report.id)) {
        next.add(report.id);
      }
      return next;
    });
  }

  protected restricted(report: IssuedReport): boolean {
    return report.accessReason === RESTRICTED;
  }

  /** El original nunca se reemplaza: una negativa actualiza la fila y no emite por su cuenta. */
  protected async download(report: IssuedReport): Promise<void> {
    this.downloading.set(report.id);
    try {
      await this.facade.download(report);
    } catch (error) {
      const problem = error as Problem;
      this.notifier.error(problem.detail);
      if (problem.code === RESTRICTED) {
        this.facade.reload();
      }
    } finally {
      this.downloading.set(null);
    }
  }

  /** La fachada de esta pantalla llega al dialogo por el inyector: no es un servicio raiz. */
  protected issue(report: IssuedReport): void {
    this.dialog
      .open(ManagerialReportDialog, { data: { report }, injector: this.injector })
      .afterClosed()
      .subscribe((issued) => {
        if (issued) {
          const cutOff = issued.cutOff ? this.cutOffFormat.format(new Date(issued.cutOff)) : '—';
          this.notifier.success(`Informe gerencial emitido con corte ${cutOff}`);
        }
      });
  }
}
