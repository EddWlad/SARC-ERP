import {
  ImportBatchResponse,
  ImportRowResponse,
  PageResultImportBatchResponse,
  PageResultImportRowResponse,
} from '../../../core/api/generated';
import { SessionSource } from './consumption';

/** Contrato del backend para importaciones de sesiones (PRD 3.1). */
export type ImportBatch = ImportBatchResponse;
export type ImportBatchPage = PageResultImportBatchResponse;
export type ImportRow = ImportRowResponse;
export type ImportRowPage = PageResultImportRowResponse;
export type RowClassification = ImportRow['classification'];
export type RowOutcome = NonNullable<ImportRow['outcome']>;

/** Decision sobre filas de la vista previa; un campo omitido no cambia esa decision. */
export interface RowDecision {
  readonly test?: boolean;
  readonly approved?: boolean;
  /** Mover la sesion al cargador que trae el archivo (solo filas que cambian de cargador). */
  readonly reassign?: boolean;
}

export const CLASSIFICATION_LABELS: Readonly<Record<RowClassification, string>> = {
  NEW: 'Nuevas',
  IDENTICAL: 'Idénticas',
  MODIFIED: 'Modificadas',
  EQUIPMENT_CHANGED: 'Cambian de cargador',
  INVALID: 'Inválidas',
  DUPLICATE: 'Duplicadas',
  PENDING_LINK: 'Pendientes de vincular',
};

/** Que pasa con cada clasificacion al confirmar (PRD 3.1). */
export const CLASSIFICATION_HINTS: Readonly<Record<RowClassification, string>> = {
  NEW: 'Se incorporan al confirmar',
  IDENTICAL: 'Ya importadas con los mismos datos; no se tocan',
  MODIFIED: 'Difieren de lo importado; se corrigen solo si las aprueba',
  EQUIPMENT_CHANGED: 'La sesión ya existe con otro cargador; solo se mueven si las reasigna',
  INVALID: 'No se pudieron interpretar; no se incorporan',
  DUPLICATE: 'Repiten una sesión anterior del mismo lote; se ignoran',
  PENDING_LINK: 'Se guardan sin equipo hasta vincularlas; no suman',
};

export const CLASSIFICATIONS = Object.keys(CLASSIFICATION_LABELS) as readonly RowClassification[];

export const OUTCOME_LABELS: Readonly<Record<RowOutcome, string>> = {
  INSERTED: 'Incorporada',
  CORRECTED: 'Corregida',
  REASSIGNED: 'Reasignada',
  UNCHANGED: 'Sin cambios',
  SKIPPED: 'Omitida',
};

/** Cantidad de filas de una clasificacion segun los conteos del lote. */
export function classificationCount(batch: ImportBatch, classification: RowClassification): number {
  switch (classification) {
    case 'NEW':
      return batch.newRows;
    case 'IDENTICAL':
      return batch.identicalRows;
    case 'MODIFIED':
      return batch.modifiedRows;
    case 'EQUIPMENT_CHANGED':
      return batch.equipmentChangedRows;
    case 'INVALID':
      return batch.invalidRows;
    case 'DUPLICATE':
      return batch.duplicateRows;
    case 'PENDING_LINK':
      return batch.pendingLinkRows;
  }
}

/** Columnas que el analista debe ver en el reporte original de cada fuente (ayuda en pantalla). */
export const SOURCE_COLUMNS: Readonly<Record<SessionSource, string>> = {
  WALLBOX: 'Serial number, Charger, Session id, Start, End, Energy (kWh)',
  ENERLINK: 'ID, INICIO, TÉRMINO, ACTIVO, CARGADOR, CONECTOR, ENERGÍA CARGADA (el resto se ignora)',
};
