import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { ConsumptionApi, EquipmentOptionPage } from '../data-access/consumption-api';

const SEARCH_DEBOUNCE_MS = 300;

/** Opciones del filtro por equipo: busca en el inventario mientras el usuario escribe. */
@Service({ autoProvided: false })
export class EquipmentOptions {
  private readonly api = inject(ConsumptionApi);

  readonly query = signal('');
  private readonly debouncedQuery = toSignal(
    toObservable(this.query).pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged()),
    { initialValue: '' },
  );
  private readonly result = httpResource<EquipmentOptionPage>(() =>
    this.api.equipmentOptionsRequest(this.debouncedQuery()),
  );

  readonly options = computed(() => (this.result.hasValue() ? this.result.value().content : []));
}
