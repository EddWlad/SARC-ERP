import {
  EquipmentImportBatchResponse,
  EquipmentImportRowResponse,
} from '../../../core/api/generated';

/** Contrato del backend para la carga masiva de equipos con la plantilla de SARC (ADR 0007). */
export type EquipmentImportBatch = EquipmentImportBatchResponse;
export type EquipmentImportRow = EquipmentImportRowResponse;
export type EquipmentRowClassification = EquipmentImportRow['classification'];
export type EquipmentRowOutcome = NonNullable<EquipmentImportRow['outcome']>;

/** Decision del analista: solo las actualizaciones de equipos existentes se aprueban. */
export interface EquipmentRowDecision {
  readonly approved: boolean;
}

export const EQUIPMENT_CLASSIFICATION_LABELS: Readonly<Record<EquipmentRowClassification, string>> =
  {
    NEW: 'Nuevos',
    IDENTICAL: 'Sin cambios',
    MODIFIED: 'Modificados',
    INVALID: 'Inválidos',
    DUPLICATE: 'Duplicados',
    DELETED_EXISTS: 'Eliminados',
    CONFLICT: 'En conflicto',
  };

/** Que pasa con cada clasificacion al confirmar (PRD 6.3). */
export const EQUIPMENT_CLASSIFICATION_HINTS: Readonly<Record<EquipmentRowClassification, string>> =
  {
    NEW: 'Se registran al confirmar, con sus identidades externas verificadas',
    IDENTICAL: 'El equipo ya tiene esos datos; no se toca',
    MODIFIED: 'El equipo existe con otros datos; se actualiza solo si lo aprueba',
    INVALID: 'Faltan datos o no cumplen las reglas del equipo; no se registran',
    DUPLICATE: 'Repiten la serie, el código, el UID/PUK o una identidad de una fila anterior',
    DELETED_EXISTS: 'La serie es de un equipo eliminado: recupérelo desde el inventario',
    CONFLICT: 'Sus datos pertenecen a otro equipo; no se fusionan identidades',
  };

export const EQUIPMENT_CLASSIFICATIONS = Object.keys(
  EQUIPMENT_CLASSIFICATION_LABELS,
) as readonly EquipmentRowClassification[];

export const EQUIPMENT_OUTCOME_LABELS: Readonly<Record<EquipmentRowOutcome, string>> = {
  CREATED: 'Registrado',
  UPDATED: 'Actualizado',
  UNCHANGED: 'Sin cambios',
  SKIPPED: 'Omitido',
};

/** Cantidad de filas de una clasificacion segun los conteos del lote. */
export function equipmentClassificationCount(
  batch: EquipmentImportBatch,
  classification: EquipmentRowClassification,
): number {
  switch (classification) {
    case 'NEW':
      return batch.newRows;
    case 'IDENTICAL':
      return batch.identicalRows;
    case 'MODIFIED':
      return batch.modifiedRows;
    case 'INVALID':
      return batch.invalidRows;
    case 'DUPLICATE':
      return batch.duplicateRows;
    case 'DELETED_EXISTS':
      return batch.deletedExistsRows;
    case 'CONFLICT':
      return batch.conflictRows;
  }
}
