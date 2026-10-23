import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { API_BASE } from '../../../core/http/api';
import {
  AdministrativeStatus,
  CreateEquipmentInput,
  Equipment,
  EquipmentFilters,
  UpdateEquipmentInput,
} from '../domain/equipment';
import { ExternalIdentity, ExternalIdentityInput } from '../domain/external-identity';
import { ScheduleInput } from '../domain/schedule';
import { equipmentFilterParams } from './equipment-filter-params';

export interface EquipmentQuery extends EquipmentFilters {
  readonly page: number;
  readonly size: number;
}

/** Acceso HTTP a `/api/v1/equipment`. Consultar: todos los roles; modificar: administradores y analistas. */
@Service()
export class EquipmentApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/equipment`;

  /** Peticion del listado para `httpResource`. */
  listRequest(query: EquipmentQuery): HttpResourceRequest {
    return {
      url: this.baseUrl,
      params: { ...equipmentFilterParams(query), page: query.page, size: query.size },
    };
  }

  detailUrl(id: string): string {
    return `${this.baseUrl}/${id}`;
  }

  identitiesUrl(id: string): string {
    return `${this.baseUrl}/${id}/external-identities`;
  }

  create(input: CreateEquipmentInput) {
    return this.http.post<Equipment>(this.baseUrl, input);
  }

  update(id: string, input: UpdateEquipmentInput) {
    return this.http.put<Equipment>(this.detailUrl(id), input);
  }

  changeStatus(id: string, version: number, status: AdministrativeStatus) {
    return this.http.patch<Equipment>(`${this.detailUrl(id)}/status`, { version, status });
  }

  defineSchedule(id: string, input: ScheduleInput) {
    return this.http.put<Equipment>(`${this.detailUrl(id)}/schedule`, input);
  }

  attachImage(id: string, version: number, file: File) {
    const body = new FormData();
    body.append('file', file);
    body.append('version', String(version));
    return this.http.put<Equipment>(`${this.detailUrl(id)}/image`, body);
  }

  removeImage(id: string, version: number) {
    return this.http.delete<Equipment>(`${this.detailUrl(id)}/image`, { params: { version } });
  }

  registerIdentity(id: string, input: ExternalIdentityInput) {
    return this.http.post<ExternalIdentity>(this.identitiesUrl(id), input);
  }
}
