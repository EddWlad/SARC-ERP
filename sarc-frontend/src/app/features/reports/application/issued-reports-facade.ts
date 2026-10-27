import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { IssuedReportsApi } from '../data-access/issued-reports-api';
import { IssuedReport, IssuedReportPage, ReportKind } from '../domain/issued-report';

const DEFAULT_PAGE_SIZE = 20;

/** Historial de reportes emitidos (RF-RPT-08), el mas reciente primero. */
@Service({ autoProvided: false })
export class IssuedReportsFacade {
  private readonly api = inject(IssuedReportsApi);

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

  async downloadUrl(report: IssuedReport): Promise<string> {
    return (await firstValueFrom(this.api.downloadUrl(report.id))).url;
  }
}
