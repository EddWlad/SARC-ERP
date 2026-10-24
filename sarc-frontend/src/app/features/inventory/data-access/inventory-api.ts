import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { API_BASE } from '../../../core/http/api';
import { EquipmentFilters } from '../domain/equipment';
import { ReportFormat } from '../../../shared/reports/report-format';
import { equipmentFilterParams } from './equipment-filter-params';

/** Acceso HTTP a `/api/v1/inventory`: indicadores y reportes con los mismos filtros del listado. */
@Service()
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/inventory`;

  summaryRequest(filters: EquipmentFilters): HttpResourceRequest {
    return { url: `${this.baseUrl}/summary`, params: equipmentFilterParams(filters) };
  }

  /** Reporte corporativo; la respuesta completa trae el nombre del archivo. */
  downloadReport(format: ReportFormat, filters: EquipmentFilters) {
    return this.http.get(`${this.baseUrl}/report`, {
      params: { ...equipmentFilterParams(filters), format },
      observe: 'response',
      responseType: 'blob',
    });
  }
}
