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
export type ImportStatus = ImportBatch['status'];
export type ImportOrigin = ImportBatch['origin'];
export type RowClassification = ImportRow['classification'];
export type RowOutcome = NonNullable<ImportRow['outcome']>;

export const IMPORT_STATUS_LABELS: Readonly<Record<ImportStatus, string>> = {
  PARSING: 'Interpretando',
  READY: 'Por revisar',
  CONFIRMING: 'Incorporando',
  COMPLETED: 'Completado',
  FAILED: 'Fallido',
  DISCARDED: 'Descartado',
};

export const ORIGIN_LABELS: Readonly<Record<ImportOrigin, string>> = {
  FILE: 'Archivo',
  PASTE: 'Pegado',
};

export const CLASSIFICATION_LABELS: Readonly<Record<RowClassification, string>> = {
  NEW: 'Nuevas',
  IDENTICAL: 'Idénticas',
  MODIFIED: 'Modificadas',
  INVALID: 'Inválidas',
  DUPLICATE: 'Duplicadas',
  PENDING_LINK: 'Pendientes de vincular',
};

/** Que pasa con cada clasificacion al confirmar (PRD 3.1). */
export const CLASSIFICATION_HINTS: Readonly<Record<RowClassification, string>> = {
  NEW: 'Se incorporan al confirmar',
  IDENTICAL: 'Ya importadas con los mismos datos; no se tocan',
  MODIFIED: 'Difieren de lo importado; se corrigen solo si las aprueba',
  INVALID: 'No se pudieron interpretar; no se incorporan',
  DUPLICATE: 'Repiten una sesión anterior del mismo lote; se ignoran',
  PENDING_LINK: 'Se guardan sin equipo hasta vincularlas; no suman',
};

export const CLASSIFICATIONS = Object.keys(CLASSIFICATION_LABELS) as readonly RowClassification[];

export const OUTCOME_LABELS: Readonly<Record<RowOutcome, string>> = {
  INSERTED: 'Incorporada',
  CORRECTED: 'Corregida',
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
    case 'INVALID':
      return batch.invalidRows;
    case 'DUPLICATE':
      return batch.duplicateRows;
    case 'PENDING_LINK':
      return batch.pendingLinkRows;
  }
}

/** El lote sigue trabajando en segundo plano: hay que volver a consultarlo. */
export function isInProgress(status: ImportStatus): boolean {
  return status === 'PARSING' || status === 'CONFIRMING';
}

/** Columnas que el analista debe ver en el reporte original de cada fuente (ayuda en pantalla). */
export const SOURCE_COLUMNS: Readonly<Record<SessionSource, string>> = {
  WALLBOX: 'Serial number, Charger, Session id, Start, End, Energy (kWh)',
  ENERLINK: 'ID, INICIO, TÉRMINO, ACTIVO, CARGADOR, CONECTOR, ENERGÍA CARGADA (el resto se ignora)',
};
