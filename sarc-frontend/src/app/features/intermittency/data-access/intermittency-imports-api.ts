import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import {
  EditImportRowRequest,
  ImportRowDecisionRequest,
  PageResultPendingRowResponse,
  ResolvePendingRowRequest,
  RowChangeResponse,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { RowClassification } from '../domain/intermittency-labels';
import { IntermittencyImportBatch, IntermittencyImportRow } from '../domain/intermittency-import';

/** Acceso HTTP a las importaciones de intermitencias (solo administradores y analistas). */
@Service()
export class IntermittencyImportsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/intermittency/import-batches`;

  upload(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<IntermittencyImportBatch>(this.baseUrl, body);
  }

  paste(text: string) {
    return this.http.post<IntermittencyImportBatch>(`${this.baseUrl}/paste`, { text });
  }

  historyRequest(page: number, size: number): HttpResourceRequest {
    return { url: this.baseUrl, params: { page, size } };
  }

  batchUrl(id: string): string {
    return `${this.baseUrl}/${id}`;
  }

  rowsRequest(
    id: string,
    classification: RowClassification | '',
    page: number,
    size: number,
  ): HttpResourceRequest {
    const params: Record<string, string | number> = { page, size };
    if (classification !== '') params['classification'] = classification;
    return { url: `${this.batchUrl(id)}/rows`, params };
  }

  /** Corrige la fila; el cuerpo lleva la version que leyo quien edita. */
  editRow(
    batch: IntermittencyImportBatch,
    row: IntermittencyImportRow,
    body: Omit<EditImportRowRequest, 'version'>,
  ) {
    return this.http.patch<RowChangeResponse>(`${this.batchUrl(batch.id)}/rows/${row.id}`, {
      version: row.version,
      ...body,
    });
  }

  linkRow(batch: IntermittencyImportBatch, row: IntermittencyImportRow, equipmentId: string) {
    return this.http.post<RowChangeResponse>(`${this.batchUrl(batch.id)}/rows/${row.id}/link`, {
      version: row.version,
      equipmentId,
    });
  }

  decideRow(
    batch: IntermittencyImportBatch,
    row: IntermittencyImportRow,
    body: Omit<ImportRowDecisionRequest, 'version'>,
  ) {
    return this.http.post<RowChangeResponse>(`${this.batchUrl(batch.id)}/rows/${row.id}/decision`, {
      version: row.version,
      ...body,
    });
  }

  /** Filas KEEP_PENDING de lotes confirmados sin resolver (FR-028). */
  pendingRows(page: number, size: number, classification: RowClassification | '' = '') {
    const params: Record<string, string | number> = { page, size };
    if (classification !== '') params['classification'] = classification;
    return this.http.get<PageResultPendingRowResponse>(`${API_BASE}/intermittency/pending-rows`, {
      params,
    });
  }

  resolveRow(
    batchId: string,
    row: IntermittencyImportRow,
    body: Omit<ResolvePendingRowRequest, 'version'>,
  ) {
    return this.http.post<RowChangeResponse>(`${this.batchUrl(batchId)}/rows/${row.id}/resolve`, {
      version: row.version,
      ...body,
    });
  }

  confirm(batch: IntermittencyImportBatch) {
    return this.http.post<IntermittencyImportBatch>(`${this.batchUrl(batch.id)}/confirm`, {
      version: batch.version,
    });
  }

  retry(batch: IntermittencyImportBatch) {
    return this.http.post<IntermittencyImportBatch>(`${this.batchUrl(batch.id)}/retry`, {
      version: batch.version,
    });
  }

  discard(batch: IntermittencyImportBatch) {
    return this.http.post<IntermittencyImportBatch>(`${this.batchUrl(batch.id)}/discard`, {
      version: batch.version,
    });
  }
}
