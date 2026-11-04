import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import {
  PageResultConnectorAliasResponse,
  PageResultUptimeImportRowResponse,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import {
  ConnectorAlias,
  DeclaredPeriodType,
  UptimeImportBatch,
  UptimeImportRow,
  UptimeRowClassification,
  UptimeRowDecision,
} from '../domain/uptime-import';

/** Acceso HTTP a importaciones, pendientes y alias de uptime (solo administradores y analistas). */
@Service()
export class UptimeImportsApi {
  private readonly http = inject(HttpClient);
  private readonly root = `${API_BASE}/uptime`;
  private readonly baseUrl = `${this.root}/imports`;

  /** Informe semanal; {@code weekStart} es el lunes (aaaa-mm-dd) de la semana que reporta. */
  upload(weekStart: string, file: File) {
    const body = new FormData();
    body.append('weekStart', weekStart);
    body.append('file', file);
    return this.http.post<UptimeImportBatch>(this.baseUrl, body);
  }

  paste(declaredPeriodType: DeclaredPeriodType, text: string) {
    return this.http.post<UptimeImportBatch>(`${this.baseUrl}/paste`, { declaredPeriodType, text });
  }

  historyRequest(page: number, size: number): HttpResourceRequest {
    return { url: this.baseUrl, params: { page, size } };
  }

  batchUrl(id: string): string {
    return `${this.baseUrl}/${id}`;
  }

  rowsRequest(
    id: string,
    classification: UptimeRowClassification | '',
    page: number,
    size: number,
  ): HttpResourceRequest {
    const params: Record<string, string | number> = { page, size };
    if (classification !== '') {
      params['classification'] = classification;
    }
    return { url: `${this.batchUrl(id)}/rows`, params };
  }

  decide(id: string, rowIds: readonly string[], decision: UptimeRowDecision) {
    return this.http.put<UptimeImportRow[]>(`${this.batchUrl(id)}/rows/decision`, {
      rowIds,
      ...decision,
    });
  }

  confirm(batch: UptimeImportBatch, exactConfirmed: boolean) {
    return this.http.post<UptimeImportBatch>(`${this.batchUrl(batch.id)}/confirm`, {
      version: batch.version,
      exactConfirmed,
    });
  }

  retry(batch: UptimeImportBatch) {
    return this.http.post<UptimeImportBatch>(`${this.batchUrl(batch.id)}/retry`, {
      version: batch.version,
    });
  }

  discard(batch: UptimeImportBatch) {
    return this.http.post<UptimeImportBatch>(`${this.batchUrl(batch.id)}/discard`, {
      version: batch.version,
    });
  }

  pendingLinks(page: number, size: number) {
    return this.http.get<PageResultUptimeImportRowResponse>(`${this.root}/pending-links`, {
      params: { page, size },
    });
  }

  link(row: UptimeImportRow, connectorId: string, rememberLabel: boolean) {
    return this.http.post<UptimeImportRow>(`${this.root}/pending-links/${row.id}/link`, {
      version: row.version,
      connectorId,
      rememberLabel,
    });
  }

  aliases(page: number, size: number) {
    return this.http.get<PageResultConnectorAliasResponse>(`${this.root}/connector-aliases`, {
      params: { page, size },
    });
  }

  updateAlias(alias: ConnectorAlias, connectorId: string, active: boolean) {
    return this.http.put<ConnectorAlias>(`${this.root}/connector-aliases/${alias.id}`, {
      version: alias.version,
      connectorId,
      active,
    });
  }
}
