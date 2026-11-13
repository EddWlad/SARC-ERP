import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import {
  CaptureResultResponse,
  CaptureWindowsRequest,
  CloseWindowRequest,
  CorrectWindowRequest,
  KiaPointResponse,
  MarkPointTestRequest,
  PointTestResultResponse,
  ReviewedDayMarkResponse,
  ReviewedDayResponse,
  WindowChangeRequest,
  WindowDetailResponse,
  WindowPageResponse,
  WindowResponse,
  WithdrawReviewedDayRequest,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { WindowState } from '../domain/intermittency-labels';
import { ReportFormat } from '../../../shared/reports/report-format';
import { PeriodPreset } from '../domain/intermittency-time';

/** Filtros del historial. `from` y `to` son dias aaaa-mm-dd; el fin es exclusivo. */
export interface WindowQuery {
  readonly equipmentIds?: readonly string[];
  readonly states?: readonly WindowState[];
  readonly from?: string;
  readonly to?: string;
  readonly page?: number;
  readonly size?: number;
}

/**
 * Filtros del tablero. Las quincenas llevan el mes; el rango personalizado, `from` y `to` (fin exclusivo);
 * los ultimos 15 dias no llevan nada mas. Sin puntos son todos.
 */
export interface SummaryQuery {
  readonly preset: PeriodPreset;
  readonly month?: string;
  readonly from?: string;
  readonly to?: string;
  readonly equipmentIds: readonly string[];
}

/** Los filtros del tablero como parametros de consulta: el informe usa exactamente los mismos. */
function summaryParams(query: SummaryQuery): Record<string, string | readonly string[]> {
  const params: Record<string, string | readonly string[]> = { preset: query.preset };
  if (query.month) params['month'] = query.month;
  if (query.from) params['from'] = query.from;
  if (query.to) params['to'] = query.to;
  if (query.equipmentIds.length > 0) params['equipmentIds'] = query.equipmentIds;
  return params;
}

/** Acceso HTTP a `/api/v1/intermittency`. */
@Service()
export class IntermittencyApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/intermittency`;

  /** Puntos de carga KIA por codigo o nombre. Lo usa cualquier usuario autenticado. */
  searchPoints(query: string, limit = 20) {
    return this.http.get<KiaPointResponse[]>(`${this.baseUrl}/points`, {
      params: { query, limit },
    });
  }

  captureWindows(body: CaptureWindowsRequest) {
    return this.http.post<CaptureResultResponse>(`${this.baseUrl}/windows`, body);
  }

  closeWindow(id: string, body: CloseWindowRequest) {
    return this.http.post<WindowResponse>(`${this.baseUrl}/windows/${id}/close`, body);
  }

  windowDetail(id: string) {
    return this.http.get<WindowDetailResponse>(`${this.baseUrl}/windows/${id}`);
  }

  correctWindow(id: string, body: CorrectWindowRequest) {
    return this.http.patch<WindowResponse>(`${this.baseUrl}/windows/${id}`, body);
  }

  /** Confirmar una ventana por revisar, marcarla o quitarle la prueba, o anularla: todo con justificacion. */
  changeWindow(
    id: string,
    action: 'confirm-review' | 'test' | 'untest' | 'annul',
    body: WindowChangeRequest,
  ) {
    return this.http.post<WindowResponse>(`${this.baseUrl}/windows/${id}/${action}`, body);
  }

  /** Marca o quita la prueba de las ventanas de un punto en un rango de dias con fin exclusivo (FR-016). */
  pointTest(equipmentId: string, mark: boolean, body: MarkPointTestRequest) {
    return this.http.post<PointTestResultResponse>(
      `${this.baseUrl}/points/${equipmentId}/${mark ? 'test' : 'untest'}`,
      body,
    );
  }

  /** Cifras del tablero; es una peticion de `httpResource`, igual que el resumen de Consumo. */
  summaryRequest(query: SummaryQuery): HttpResourceRequest {
    return { url: `${this.baseUrl}/summary`, params: summaryParams(query) };
  }

  /** PDF gerencial o Excel con los mismos filtros del tablero; la respuesta completa trae el nombre del archivo. */
  downloadReport(format: ReportFormat, query: SummaryQuery) {
    return this.http.get(`${this.baseUrl}/report`, {
      params: { ...summaryParams(query), format },
      observe: 'response',
      responseType: 'blob',
    });
  }

  /** Disponibilidad estimada de prueba para los mismos filtros del tablero; solo con el permiso (FR-006). */
  availabilityRequest(query: SummaryQuery): HttpResourceRequest {
    return { url: `${this.baseUrl}/availability`, params: summaryParams(query) };
  }

  /** Un registro por cada dia de `[from, to)`, con o sin marca; sin rango, los ultimos 31 dias. */
  reviewedDays(from?: string, to?: string) {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return this.http.get<ReviewedDayResponse[]>(`${this.baseUrl}/reviewed-days`, { params });
  }

  /** Marca un dia terminado como revisado para toda la red KIA; avisa de los lotes sin confirmar que lo tocan. */
  markDay(day: string) {
    return this.http.put<ReviewedDayMarkResponse>(`${this.baseUrl}/reviewed-days/${day}`, null);
  }

  /** Retira la marca de un dia con una justificacion obligatoria. */
  withdrawDay(day: string, body: WithdrawReviewedDayRequest) {
    return this.http.post<ReviewedDayResponse>(
      `${this.baseUrl}/reviewed-days/${day}/withdraw`,
      body,
    );
  }

  listWindows(query: WindowQuery = {}) {
    const params: Record<string, string | number | readonly string[]> = {};
    if (query.equipmentIds?.length) params['equipmentIds'] = query.equipmentIds;
    if (query.states?.length) params['states'] = query.states;
    if (query.from) params['from'] = query.from;
    if (query.to) params['to'] = query.to;
    params['page'] = query.page ?? 0;
    params['size'] = query.size ?? 20;
    return this.http.get<WindowPageResponse>(`${this.baseUrl}/windows`, { params });
  }
}
