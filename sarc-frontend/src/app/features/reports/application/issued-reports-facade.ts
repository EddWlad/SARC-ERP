import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { FileSaver } from '../../../core/files/file-saver';
import { ReportFormat } from '../../../shared/reports/report-format';
import { IssuedReportsApi } from '../data-access/issued-reports-api';
import {
  GeneratedReportInfo,
  IssuedReport,
  IssuedReportPage,
  ManagerialReportCriteria,
  ReportEquipmentOption,
  ReportKind,
} from '../domain/issued-report';

const DEFAULT_PAGE_SIZE = 20;

/** Historial de reportes emitidos (RF-RPT-08), el mas reciente primero. */
@Service({ autoProvided: false })
export class IssuedReportsFacade {
  private readonly api = inject(IssuedReportsApi);
  private readonly files = inject(FileSaver);

  readonly kind = signal<ReportKind | ''>('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  private readonly result = httpResource<IssuedReportPage>(() =>
    this.api.listRequest(this.kind(), this.pageIndex(), this.pageSize()),
  );

  readonly reports = computed(() => (this.result.hasValue() ? this.result.value().content : []));
  readonly totalElements = computed(() =>
    this.result.hasValue() ? this.result.value().totalElements : 0,
  );
  readonly loading = this.result.isLoading;
  readonly failed = computed(() => this.result.error() !== undefined);

  filterKind(kind: ReportKind | ''): void {
    this.kind.set(kind);
    this.pageIndex.set(0);
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.result.reload();
  }

  /** Rechaza con el `Problem` del backend, que en descargas llega como Blob. */
  download(report: IssuedReport): Promise<void> {
    return this.files.download(this.api.download(report.id), report.fileName);
  }

  async generateConsumptionReport(
    criteria: ManagerialReportCriteria,
    format: ReportFormat,
  ): Promise<GeneratedReportInfo> {
    const result = await firstValueFrom(this.api.generateConsumptionReport(criteria, format));
    this.files.save(result, format === 'PDF' ? 'consumo.pdf' : 'consumo.xlsx');
    this.reload();
    return {
      id: result.headers.get('X-Report-Id'),
      cutOff: result.headers.get('X-Report-Cut-Off'),
    };
  }

  async searchEquipment(text: string): Promise<readonly ReportEquipmentOption[]> {
    const query = text.trim();
    return query.length < 2 ? [] : (await firstValueFrom(this.api.equipmentOptions(query))).content;
  }

  findEquipment(id: string): Promise<ReportEquipmentOption> {
    return firstValueFrom(this.api.equipment(id));
  }
}
