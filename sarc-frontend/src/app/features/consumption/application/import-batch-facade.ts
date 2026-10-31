import { inject, Service, signal, WritableSignal } from '@angular/core';

import { ImportBatchState } from '../../../shared/imports/import-batch-state';
import { EnergyImportsApi } from '../data-access/energy-imports-api';
import { ImportBatch, ImportRow, RowClassification, RowDecision } from '../domain/energy-import';

/**
 * Vista previa y seguimiento de un lote de sesiones (ruta `/consumo/importaciones/:id`); el ciclo
 * y el sondeo son los comunes a todas las importaciones.
 */
@Service({ autoProvided: false })
export class ImportBatchFacade extends ImportBatchState<
  ImportBatch,
  ImportRow,
  RowClassification,
  RowDecision
> {
  readonly aboveThreshold: WritableSignal<boolean | undefined>;

  constructor() {
    const api = inject(EnergyImportsApi);
    const high = signal<boolean | undefined>(undefined);
    super({
      batchUrl: (id) => api.batchUrl(id),
      rowsRequest: (id, classification, page, size) =>
        api.rowsRequest(id, classification, page, size, high()),
      decide: (id, rows, decision) => api.decide(id, rows, decision),
      confirm: (batch) => api.confirm(batch),
      retry: (batch) => api.retry(batch),
      discard: (batch) => api.discard(batch),
    });
    this.aboveThreshold = high;
  }

  filterAboveThreshold(value: boolean | ''): void {
    this.aboveThreshold.set(value === '' ? undefined : value);
    this.pageIndex.set(0);
  }
}
