import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import {
  BatchValidationRequest,
  BatchValidationResult,
  ClassificationResponse,
  ClassifyRequest,
  CorrectMeasurementRequest,
  GridRowResponse,
  JustifiedVersionRequest,
  MeasurementDetailResponse,
  MeasurementHistoryResponse,
  MeasurementResponse,
  PageResultConnectorResultResponse,
  PageResultEquipmentResultResponse,
  PageResultExclusionResponse,
  PageResultMeasurementDetailResponse,
  ParsedBlockResponse,
  RegisterMeasurementRequest,
  ScopeConnectorResponse,
  UptimeSummaryResponse,
  ValidateMeasurementRequest,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { ReportFormat } from '../../../shared/reports/report-format';
import { MeasurementQueue, PeriodType } from '../domain/uptime-labels';

export interface GridQuery {
  readonly periodStart: string;
  readonly periodEnd: string;
  readonly network?: string;
  readonly type?: string;
  readonly equipmentId?: string;
  readonly showInactive?: boolean;
}

export interface MeasurementQuery {
  readonly queue?: MeasurementQueue;
  readonly connectorId?: string;
  readonly equipmentId?: string;
  readonly page?: number;
  readonly size?: number;
}

/** Filtros comunes de indicadores. `to` es exclusivo; sin `periodKind` aplica la apertura. */
export interface UptimeFilters {
  readonly periodKind?: 'OFFICIAL' | 'PROVISIONAL';
  readonly periodType?: PeriodType;
  readonly from?: string;
  readonly to?: string;
  readonly network?: string;
  readonly type?: string;
  readonly brand?: string;
  readonly equipmentId?: string;
  readonly connectorId?: string;
}

/** Acceso HTTP a `/api/v1/uptime`. */
@Service()
export class UptimeApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/uptime`;

  grid(query: GridQuery) {
    return this.http.get<GridRowResponse[]>(`${this.baseUrl}/grid`, { params: { ...query } });
  }

  register(request: RegisterMeasurementRequest) {
    return this.http.post<MeasurementResponse>(`${this.baseUrl}/measurements`, request);
  }

  /** Reporte corporativo con el mismo filtro del tablero; la respuesta completa trae el nombre. */
  downloadReport(format: ReportFormat, filters: UptimeFilters) {
    return this.http.get(`${this.baseUrl}/report`, {
      params: { ...filters, format },
      observe: 'response',
      responseType: 'blob',
    });
  }

  parseBlock(text: string) {
    return this.http.post<ParsedBlockResponse>(`${this.baseUrl}/measurements/parse`, { text });
  }

  correct(id: string, request: CorrectMeasurementRequest) {
    return this.http.post<MeasurementResponse>(
      `${this.baseUrl}/measurements/${id}/corrections`,
      request,
    );
  }

  classify(request: ClassifyRequest) {
    return this.http.post<ClassificationResponse[]>(
      `${this.baseUrl}/measurements/classifications`,
      request,
    );
  }

  validate(id: string, request: ValidateMeasurementRequest) {
    return this.http.post<MeasurementResponse>(
      `${this.baseUrl}/measurements/${id}/validate`,
      request,
    );
  }

  validateBatch(request: BatchValidationRequest) {
    return this.http.post<BatchValidationResult>(
      `${this.baseUrl}/measurements/validation`,
      request,
    );
  }

  exclude(id: string, request: JustifiedVersionRequest) {
    return this.http.post<MeasurementResponse>(
      `${this.baseUrl}/measurements/${id}/exclude`,
      request,
    );
  }

  restore(id: string, request: JustifiedVersionRequest) {
    return this.http.post<MeasurementResponse>(
      `${this.baseUrl}/measurements/${id}/restore`,
      request,
    );
  }

  search(query: MeasurementQuery) {
    return this.http.get<PageResultMeasurementDetailResponse>(`${this.baseUrl}/measurements`, {
      params: { ...query },
    });
  }

  detail(id: string) {
    return this.http.get<MeasurementDetailResponse>(`${this.baseUrl}/measurements/${id}`);
  }

  summary(filters: UptimeFilters) {
    return this.http.get<UptimeSummaryResponse>(`${this.baseUrl}/summary`, {
      params: { ...filters },
    });
  }

  connectorResults(filters: UptimeFilters, page: number, size: number) {
    return this.http.get<PageResultConnectorResultResponse>(`${this.baseUrl}/results/connectors`, {
      params: { ...filters, page, size },
    });
  }

  equipmentResults(filters: UptimeFilters, page: number, size: number) {
    return this.http.get<PageResultEquipmentResultResponse>(`${this.baseUrl}/results/equipment`, {
      params: { ...filters, page, size },
    });
  }

  exclusionResults(filters: UptimeFilters, page: number, size: number) {
    return this.http.get<PageResultExclusionResponse>(`${this.baseUrl}/results/exclusions`, {
      params: { ...filters, page, size },
    });
  }

  scope(q: string) {
    return this.http.get<ScopeConnectorResponse[]>(`${this.baseUrl}/scope/connectors`, {
      params: q ? { q } : {},
    });
  }

  revisions(id: string) {
    return this.http.get<MeasurementHistoryResponse>(
      `${this.baseUrl}/measurements/${id}/revisions`,
    );
  }
}
