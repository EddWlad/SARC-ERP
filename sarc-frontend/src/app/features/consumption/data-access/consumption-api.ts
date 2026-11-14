import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import {
  PageResultEquipmentSummaryResponse,
  PendingLinkResponse,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { ReportFormat } from '../../../shared/reports/report-format';
import {
  ConsumptionFilters,
  Session,
  SessionState,
  EnergyReview,
  EnergyReviewStatus,
  EnergyDecision,
  SessionChanges,
  SessionDetail,
} from '../domain/consumption';
import { DateRange } from '../domain/period';

export interface SessionQuery {
  readonly range: DateRange;
  readonly filters: ConsumptionFilters;
  readonly state: SessionState | '';
  readonly energyReview?: EnergyReviewStatus | '';
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
    if (query.energyReview) params['energyReview'] = query.energyReview;
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

  /** Una fuente no preferente para su equipo suma solo cuando un analista la aprueba. */
  approveSource(session: Session) {
    return this.http.post<Session>(`${this.baseUrl}/sessions/${session.id}/source-approval`, {
      version: session.version,
    });
  }

  energyReviews(id: string) {
    return this.http.get<EnergyReview[]>(`${this.baseUrl}/sessions/${id}/energy-reviews`);
  }

  decideEnergy(session: Session, decision: EnergyDecision, justification: string) {
    return this.http.post<Session>(`${this.baseUrl}/sessions/${session.id}/energy-review`, {
      decision,
      justification: justification.trim(),
      version: session.version,
    });
  }

  /** Detalle con baja y revisiones cuando la cuenta puede verlas (Feature 009). */
  detail(id: string) {
    return this.http.get<SessionDetail>(`${this.baseUrl}/sessions/${id}`);
  }

  /** Edicion con motivo sobre la version que se abrio; solo viajan los campos cambiados. */
  edit(session: Session, changes: SessionChanges, reason: string) {
    return this.http.patch<Session>(`${this.baseUrl}/sessions/${session.id}`, {
      ...changes,
      reason: reason.trim(),
      version: session.version,
    });
  }

  /** Baja logica con motivo: deja de contar en indicadores e informes. */
  delete(session: Session, reason: string) {
    return this.http.post<Session>(`${this.baseUrl}/sessions/${session.id}/deletion`, {
      reason: reason.trim(),
      version: session.version,
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
