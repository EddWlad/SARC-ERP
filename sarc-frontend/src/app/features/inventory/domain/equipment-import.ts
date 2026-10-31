import {
  EquipmentImportBatchResponse,
  EquipmentImportRowResponse,
} from '../../../core/api/generated';
import { ImportConfirmSummary } from '../../../shared/imports/import-confirm-summary';

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
    IDENTITY_COMPLETED: 'Completan identidad',
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
    MODIFIED:
      'El equipo existe con otros datos; se actualiza solo si lo aprueba. Sin aprobar no se registra tampoco su identidad',
    IDENTITY_COMPLETED:
      'Solo agregan la identidad Enerlink a un equipo existente; se aplican al confirmar, sin aprobación',
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
  IDENTITY_ADDED: 'Identidad añadida',
  UNCHANGED: 'Sin cambios',
  SKIPPED: 'Omitido',
};

/**
 * Que hace confirmar esta carga masiva, agrupada por destino y con los conteos que trae el lote. El
 * total de filas aprobadas no esta en la respuesta (es una decision por fila y solo se cargan las de
 * la pagina visible): el grupo condicional lo dice en vez de estimarlo.
 */
export function equipmentConfirmSummary(batch: EquipmentImportBatch): ImportConfirmSummary {
  return {
    title: 'Confirmar carga masiva de equipos',
    fileName: batch.fileName,
    totalRows: batch.totalRows,
    confirmLabel: 'Confirmar carga',
    groups: [
      {
        title: 'Se aplican al confirmar',
        tone: 'applies',
        items: [
          {
            label: EQUIPMENT_CLASSIFICATION_LABELS.NEW,
            count: batch.newRows,
            detail: 'se registran con sus identidades externas verificadas',
          },
          {
            label: EQUIPMENT_CLASSIFICATION_LABELS.IDENTITY_COMPLETED,
            count: batch.identityCompletedRows,
            detail: 'solo agregan la identidad Enerlink a un equipo que ya existe',
          },
        ],
      },
      {
        title: 'Solo los que haya aprobado fila por fila',
        note: 'El lote no lleva el total de aprobaciones: un modificado sin aprobar se omite completo, también su identidad.',
        tone: 'conditional',
        items: [
          {
            label: EQUIPMENT_CLASSIFICATION_LABELS.MODIFIED,
            count: batch.modifiedRows,
            detail: 'se actualizan solo si los aprobó',
          },
        ],
      },
      {
        title: 'No se tocan',
        tone: 'untouched',
        items: [
          { label: EQUIPMENT_CLASSIFICATION_LABELS.IDENTICAL, count: batch.identicalRows },
          { label: EQUIPMENT_CLASSIFICATION_LABELS.DUPLICATE, count: batch.duplicateRows },
          {
            label: EQUIPMENT_CLASSIFICATION_LABELS.INVALID,
            count: batch.invalidRows,
            detail: 'faltan datos o no cumplen las reglas del equipo',
          },
          {
            label: EQUIPMENT_CLASSIFICATION_LABELS.CONFLICT,
            count: batch.conflictRows,
            detail: 'sus datos pertenecen a otro equipo; no se fusionan identidades',
          },
          {
            label: EQUIPMENT_CLASSIFICATION_LABELS.DELETED_EXISTS,
            count: batch.deletedExistsRows,
            detail: 'la serie es de un equipo eliminado: recupérelo desde el inventario',
          },
        ],
      },
    ],
  };
}

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
    case 'IDENTITY_COMPLETED':
      return batch.identityCompletedRows;
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
