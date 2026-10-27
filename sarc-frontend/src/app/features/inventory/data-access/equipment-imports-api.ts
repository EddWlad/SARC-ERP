import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { API_BASE } from '../../../core/http/api';
import {
  EquipmentImportBatch,
  EquipmentImportRow,
  EquipmentRowClassification,
  EquipmentRowDecision,
} from '../domain/equipment-import';

/** Acceso HTTP a `/api/v1/equipment/imports` (solo administradores y analistas). */
@Service()
export class EquipmentImportsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/equipment/imports`;

  /** Plantilla XLSX; la respuesta completa trae el nombre del archivo. */
  downloadTemplate() {
    return this.http.get(`${this.baseUrl}/template`, { observe: 'response', responseType: 'blob' });
  }

  upload(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<EquipmentImportBatch>(this.baseUrl, body);
  }

  paste(text: string) {
    return this.http.post<EquipmentImportBatch>(`${this.baseUrl}/paste`, { text });
  }

  historyRequest(page: number, size: number): HttpResourceRequest {
    return { url: this.baseUrl, params: { page, size } };
  }

  batchUrl(id: string): string {
    return `${this.baseUrl}/${id}`;
  }

  rowsRequest(
    id: string,
    classification: EquipmentRowClassification | '',
    page: number,
    size: number,
  ): HttpResourceRequest {
    const params: Record<string, string | number> = { page, size };
    if (classification !== '') {
      params['classification'] = classification;
    }
    return { url: `${this.batchUrl(id)}/rows`, params };
  }

  decide(id: string, rowIds: readonly string[], decision: EquipmentRowDecision) {
    return this.http.put<EquipmentImportRow[]>(`${this.batchUrl(id)}/rows/decision`, {
      rowIds,
      approved: decision.approved,
    });
  }

  confirm(batch: EquipmentImportBatch) {
    return this.http.post<EquipmentImportBatch>(`${this.batchUrl(batch.id)}/confirm`, {
      version: batch.version,
    });
  }

  retry(batch: EquipmentImportBatch) {
    return this.http.post<EquipmentImportBatch>(`${this.batchUrl(batch.id)}/retry`, {
      version: batch.version,
    });
  }

  discard(batch: EquipmentImportBatch) {
    return this.http.post<EquipmentImportBatch>(`${this.batchUrl(batch.id)}/discard`, {
      version: batch.version,
    });
  }
}
