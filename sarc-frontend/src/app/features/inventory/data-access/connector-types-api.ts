import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { API_BASE } from '../../../core/http/api';
import {
  ConnectorType,
  CreateConnectorTypeInput,
  UpdateConnectorTypeInput,
} from '../domain/connector-type';

/** Acceso HTTP al catalogo `/api/v1/connector-types` (ampliable por administradores y analistas). */
@Service()
export class ConnectorTypesApi {
  private readonly http = inject(HttpClient);

  readonly listUrl = `${API_BASE}/connector-types`;

  create(input: CreateConnectorTypeInput) {
    return this.http.post<ConnectorType>(this.listUrl, input);
  }

  update(id: string, input: UpdateConnectorTypeInput) {
    return this.http.patch<ConnectorType>(`${this.listUrl}/${id}`, input);
  }
}
