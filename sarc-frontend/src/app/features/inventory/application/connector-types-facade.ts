import { httpResource } from '@angular/common/http';
import { computed, inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ConnectorTypesApi } from '../data-access/connector-types-api';
import {
  ConnectorType,
  ConnectorTypeStatus,
  CreateConnectorTypeInput,
} from '../domain/connector-type';

/**
 * Catalogo de tipos de conector. Es compartido: lo usan la pantalla del catalogo y los formularios
 * de equipos, que solo ofrecen los tipos activos.
 */
@Service()
export class ConnectorTypesFacade {
  private readonly api = inject(ConnectorTypesApi);
  private readonly result = httpResource<ConnectorType[]>(() => this.api.listUrl);

  readonly all = computed(() => (this.result.hasValue() ? this.result.value() : []));
  readonly active = computed(() => this.all().filter((type) => type.status === 'ACTIVE'));
  readonly loading = this.result.isLoading;
  readonly failed = computed(() => this.result.error() !== undefined);

  reload(): void {
    this.result.reload();
  }

  async create(input: CreateConnectorTypeInput): Promise<ConnectorType> {
    const created = await firstValueFrom(this.api.create(input));
    this.reload();
    return created;
  }

  async rename(type: ConnectorType, name: string): Promise<ConnectorType> {
    const updated = await firstValueFrom(this.api.update(type.id, { version: type.version, name }));
    this.reload();
    return updated;
  }

  async changeStatus(type: ConnectorType, status: ConnectorTypeStatus): Promise<ConnectorType> {
    const updated = await firstValueFrom(
      this.api.update(type.id, { version: type.version, status }),
    );
    this.reload();
    return updated;
  }
}
