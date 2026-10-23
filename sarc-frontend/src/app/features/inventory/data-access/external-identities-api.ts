import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { API_BASE } from '../../../core/http/api';
import { ExternalIdentity } from '../domain/external-identity';

/** Acceso HTTP a `/api/v1/external-identities` (verificar y quitar: administradores y analistas). */
@Service()
export class ExternalIdentitiesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/external-identities`;

  verify(id: string, version: number) {
    return this.http.post<ExternalIdentity>(`${this.baseUrl}/${id}/verify`, { version });
  }

  /** Solo se pueden quitar las identidades sin verificar. */
  remove(id: string) {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
