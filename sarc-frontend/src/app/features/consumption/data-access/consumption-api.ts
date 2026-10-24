import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import {
  PageResultEquipmentSummaryResponse,
  PendingLinkResponse,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { ReportFormat } from '../../../shared/reports/report-format';
import { ConsumptionFilters, Session, SessionState } from '../domain/consumption';
import { DateRange } from '../domain/period';

export interface SessionQuery {
  readonly range: DateRange;
  readonly filters: ConsumptionFilters;
  readonly state: SessionState | '';
  readonly text: string;
  readonly page: number;
  readonly size: number;
}

/** Acceso HTTP a `/api/v1/energy` (tablero, historico y reportes: todos los roles). */
@Service()
export class ConsumptionApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/energy`;

  summaryRequest(range: DateRange, filters: ConsumptionFilters): HttpResourceRequest {
    return { url: `${this.baseUrl}/summary`, params: { ...range, ...filterParams(filters) } };
  }

  /** Reporte corporativo con el mismo filtro del tablero; la respuesta completa trae el nombre. */
  downloadReport(format: ReportFormat, range: DateRange, filters: ConsumptionFilters) {
    return this.http.get(`${this.baseUrl}/report`, {
      params: { ...range, ...filterParams(filters), format },
      observe: 'response',
      responseType: 'blob',
    });
  }

  sessionsRequest(query: SessionQuery): HttpResourceRequest {
    const params: Record<string, string | number> = {
      ...query.range,
      ...filterParams(query.filters),
      page: query.page,
      size: query.size,
    };
    if (query.state !== '') {
      params['state'] = query.state;
    }
    if (query.text.trim() !== '') {
      params['text'] = query.text.trim();
    }
    return { url: `${this.baseUrl}/sessions`, params };
  }

  markTest(session: Session, test: boolean) {
    return this.http.patch<Session>(`${this.baseUrl}/sessions/${session.id}/test`, {
      version: session.version,
      test,
    });
  }

  linkPending() {
    return this.http.post<PendingLinkResponse>(`${this.baseUrl}/sessions/link-pending`, null);
  }

  /** Equipos del inventario que coinciden con el texto, para el filtro por equipo. */
  equipmentOptionsRequest(text: string): HttpResourceRequest | undefined {
    const term = text.trim();
    return term.length < 2
      ? undefined
      : { url: `${API_BASE}/equipment`, params: { text: term, size: 10 } };
  }
}

export type EquipmentOptionPage = PageResultEquipmentSummaryResponse;

function filterParams(filters: ConsumptionFilters): Record<string, string> {
  const params: Record<string, string> = {};
  for (const key of ['network', 'type', 'source', 'equipmentId'] as const) {
    if (filters[key] !== '') {
      params[key] = filters[key];
    }
  }
  return params;
}
