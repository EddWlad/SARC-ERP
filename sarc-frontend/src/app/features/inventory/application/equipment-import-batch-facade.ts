import { inject, Service } from '@angular/core';

import { ImportBatchState } from '../../../shared/imports/import-batch-state';
import { EquipmentImportsApi } from '../data-access/equipment-imports-api';
import {
  EquipmentImportBatch,
  EquipmentImportRow,
  EquipmentRowClassification,
  EquipmentRowDecision,
} from '../domain/equipment-import';

/**
 * Vista previa y seguimiento de una carga masiva de equipos (ruta
 * `/inventario/importaciones/:id`); el ciclo y el sondeo son los comunes a todas las importaciones.
 */
@Service({ autoProvided: false })
export class EquipmentImportBatchFacade extends ImportBatchState<
  EquipmentImportBatch,
  EquipmentImportRow,
  EquipmentRowClassification,
  EquipmentRowDecision
> {
  constructor() {
    super(inject(EquipmentImportsApi));
  }
}
