import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { computed, inject, Service } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom, map, Observable } from 'rxjs';

import { EquipmentApi } from '../data-access/equipment-api';
import { ExternalIdentitiesApi } from '../data-access/external-identities-api';
import {
  AdministrativeStatus,
  CreateEquipmentInput,
  Equipment,
  UpdateEquipmentInput,
} from '../domain/equipment';
import { ExternalIdentity, ExternalIdentityInput } from '../domain/external-identity';
import { ScheduleInput } from '../domain/schedule';

/**
 * Estado de la ficha de un equipo (ruta `/inventario/:id`) y sus operaciones. Cada cambio envia la
 * version leida (bloqueo optimista) y la ficha se actualiza con la respuesta del backend.
 */
@Service({ autoProvided: false })
export class EquipmentDetailFacade {
  private readonly api = inject(EquipmentApi);
  private readonly identitiesApi = inject(ExternalIdentitiesApi);

  readonly id = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('id'))), {
    initialValue: null,
  });

  private readonly equipmentResult = httpResource<Equipment>(() => {
    const id = this.id();
    return id === null ? undefined : this.api.detailUrl(id);
  });
  private readonly identitiesResult = httpResource<ExternalIdentity[]>(() => {
    const id = this.id();
    return id === null ? undefined : this.api.identitiesUrl(id);
  });

  readonly equipment = computed(() =>
    this.equipmentResult.hasValue() ? this.equipmentResult.value() : undefined,
  );
  readonly loading = this.equipmentResult.isLoading;
  readonly notFound = computed(() => {
    const error = this.equipmentResult.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });
  readonly failed = computed(() => this.equipmentResult.error() !== undefined && !this.notFound());
  readonly identities = computed(() =>
    this.identitiesResult.hasValue() ? this.identitiesResult.value() : [],
  );

  reload(): void {
    this.equipmentResult.reload();
    this.identitiesResult.reload();
  }

  update(input: Omit<UpdateEquipmentInput, 'version'>): Promise<Equipment> {
    return this.change((current) =>
      this.api.update(current.id, { ...input, version: current.version }),
    );
  }

  changeStatus(status: AdministrativeStatus): Promise<Equipment> {
    return this.change((current) => this.api.changeStatus(current.id, current.version, status));
  }

  defineSchedule(input: Omit<ScheduleInput, 'version'>): Promise<Equipment> {
    return this.change((current) =>
      this.api.defineSchedule(current.id, { ...input, version: current.version }),
    );
  }

  attachImage(file: File): Promise<Equipment> {
    return this.change((current) => this.api.attachImage(current.id, current.version, file));
  }

  removeImage(): Promise<Equipment> {
    return this.change((current) => this.api.removeImage(current.id, current.version));
  }

  /** Registra un equipo nuevo que reemplaza fisicamente al de esta ficha (debe estar fuera de servicio). */
  registerReplacement(
    input: Omit<CreateEquipmentInput, 'replacesEquipmentId'>,
  ): Promise<Equipment> {
    return firstValueFrom(
      this.api.create({ ...input, replacesEquipmentId: this.requireEquipment().id }),
    );
  }

  async registerIdentity(input: ExternalIdentityInput): Promise<ExternalIdentity> {
    const created = await firstValueFrom(
      this.api.registerIdentity(this.requireEquipment().id, input),
    );
    this.identitiesResult.reload();
    return created;
  }

  async verifyIdentity(identity: ExternalIdentity): Promise<void> {
    await firstValueFrom(this.identitiesApi.verify(identity.id, identity.version));
    this.identitiesResult.reload();
  }

  async removeIdentity(identity: ExternalIdentity): Promise<void> {
    await firstValueFrom(this.identitiesApi.remove(identity.id));
    this.identitiesResult.reload();
  }

  private async change(request: (current: Equipment) => Observable<Equipment>): Promise<Equipment> {
    const updated = await firstValueFrom(request(this.requireEquipment()));
    this.equipmentResult.set(updated);
    return updated;
  }

  private requireEquipment(): Equipment {
    const current = this.equipment();
    if (current === undefined) {
      throw new Error('La ficha del equipo aun no esta cargada');
    }
    return current;
  }
}
