import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { PageResultEquipmentSummaryResponse, EquipmentResponse } from '../../../core/api/generated';
import { ReportFormat } from '../../../shared/reports/report-format';
import { API_BASE } from '../../../core/http/api';
import { ReportKind, ManagerialReportCriteria } from '../domain/issued-report';

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

  /** Entrega binaria autorizada en cada peticion; no usa enlaces del proveedor. */
  download(id: string) {
    return this.http.get(`${this.baseUrl}/${id}/download`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  /** Mismo endpoint que Consumo: el servidor decide la audiencia por el usuario actual. */
  generateConsumptionReport(criteria: ManagerialReportCriteria, format: ReportFormat) {
    const params: Record<string, string> = { from: criteria.from, to: criteria.to, format };
    for (const key of ['network', 'type', 'source', 'equipmentId'] as const) {
      const value = criteria[key];
      if (value) {
        params[key] = value;
      }
    }
    return this.http.get(`${API_BASE}/energy/report`, {
      params,
      observe: 'response',
      responseType: 'blob',
    });
  }

  equipmentOptions(text: string) {
    return this.http.get<PageResultEquipmentSummaryResponse>(`${API_BASE}/equipment`, {
      params: { text, size: 10 },
    });
  }

  equipment(id: string) {
    return this.http.get<EquipmentResponse>(`${API_BASE}/equipment/${id}`);
  }
}
