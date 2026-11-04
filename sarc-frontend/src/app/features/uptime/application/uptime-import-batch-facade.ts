import { inject, Service, signal, WritableSignal } from '@angular/core';

import { ImportBatchState } from '../../../shared/imports/import-batch-state';
import { UptimeImportsApi } from '../data-access/uptime-imports-api';
import {
  UptimeImportBatch,
  UptimeImportRow,
  UptimeRowClassification,
  UptimeRowDecision,
} from '../domain/uptime-import';

/** Vista previa y seguimiento de un lote de uptime; el ciclo es el comun a las importaciones. */
@Service({ autoProvided: false })
export class UptimeImportBatchFacade extends ImportBatchState<
  UptimeImportBatch,
  UptimeImportRow,
  UptimeRowClassification,
  UptimeRowDecision
> {
  /** La persona afirma que el lote es una semana exacta; el backend decide fila por fila. */
  readonly exactConfirmed: WritableSignal<boolean>;

  constructor() {
    const api = inject(UptimeImportsApi);
    const exact = signal(false);
    super({
      batchUrl: (id) => api.batchUrl(id),
      rowsRequest: (id, classification, page, size) =>
        api.rowsRequest(id, classification, page, size),
      decide: (id, rows, decision) => api.decide(id, rows, decision),
      confirm: (batch) => api.confirm(batch, exact()),
      retry: (batch) => api.retry(batch),
      discard: (batch) => api.discard(batch),
    });
    this.exactConfirmed = exact;
  }
}
