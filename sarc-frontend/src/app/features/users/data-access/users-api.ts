import { HttpClient, HttpResourceRequest } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { TemporaryPasswordResponse } from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { CreatedUser, CreateUserInput, UpdateUserInput, User, UserFilters } from '../domain/user';

export interface UserQuery extends UserFilters {
  readonly page: number;
  readonly size: number;
}

/** Acceso HTTP a `/api/v1/users` (solo ADMINISTRADOR). */
@Service()
export class UsersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/users`;

  /** Peticion del listado para `httpResource`; omite los filtros vacios. */
  listRequest(query: UserQuery): HttpResourceRequest {
    const params: Record<string, string | number> = { page: query.page, size: query.size };
    if (query.text.trim() !== '') {
      params['text'] = query.text.trim();
    }
    if (query.role !== '') {
      params['role'] = query.role;
    }
    if (query.status !== '') {
      params['status'] = query.status;
    }
    return { url: this.baseUrl, params };
  }

  create(input: CreateUserInput) {
    return this.http.post<CreatedUser>(this.baseUrl, input);
  }

  update(id: string, input: UpdateUserInput) {
    return this.http.patch<User>(`${this.baseUrl}/${id}`, input);
  }

  resetPassword(id: string) {
    return this.http.post<TemporaryPasswordResponse>(`${this.baseUrl}/${id}/reset-password`, null);
  }
}
