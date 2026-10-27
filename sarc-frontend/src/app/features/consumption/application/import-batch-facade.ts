import { inject, Service } from '@angular/core';

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
  constructor() {
    super(inject(EnergyImportsApi));
  }
}
