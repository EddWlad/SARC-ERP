import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { DownloadResponse } from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { ReportKind } from '../domain/issued-report';

/** Acceso HTTP a `/api/v1/reports/issued`. */
@Service()
export class IssuedReportsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/reports/issued`;

  listRequest(kind: ReportKind | '', page: number, size: number): HttpResourceRequest {
    return {
      url: this.baseUrl,
      params: kind === '' ? { page, size } : { kind, page, size },
    };
  }

  /** URL firmada y de corta duracion de la copia archivada. */
  downloadUrl(id: string) {
    return this.http.get<DownloadResponse>(`${this.baseUrl}/${id}/download`);
  }
}
