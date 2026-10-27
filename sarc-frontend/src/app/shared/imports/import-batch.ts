import { ImportBatchResponse } from '../../core/api/generated';

/**
 * Ciclo comun de las importaciones de SARC (backend `shared/importing/batch`): consumos y equipos
 * pasan por interpretar, revisar y confirmar con los mismos estados y acciones.
 */
export type ImportStatus = ImportBatchResponse['status'];
export type ImportOrigin = ImportBatchResponse['origin'];

/** Lo que toda importacion comparte, sea de consumos o de equipos. */
export interface ImportBatchLike {
  readonly id: string;
  readonly origin: ImportOrigin;
  readonly fileName: string;
  readonly status: ImportStatus;
  readonly failureMessage?: string | null;
  readonly totalRows: number;
  readonly appliedRows: number;
  readonly createdAt: string;
  readonly confirmedAt?: string | null;
  readonly version: number;
}

/** Fila de una vista previa que el analista puede aprobar. */
export interface ImportRowLike {
  readonly id: string;
  readonly requiresApproval: boolean;
  readonly approved: boolean;
}

/** TRD 09.1: el backend rechaza originales de mas de 20 MiB; las pantallas avisan antes de subir. */
export const MAX_IMPORT_BYTES = 20 * 1024 * 1024;

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

/** El lote sigue trabajando en segundo plano: hay que volver a consultarlo. */
export function isInProgress(status: ImportStatus): boolean {
  return status === 'PARSING' || status === 'CONFIRMING';
}

/** Se descarta en revision, o si fallo antes de confirmarse (fallo al leerse). */
export function canDiscard(batch: ImportBatchLike): boolean {
  return batch.status === 'READY' || (batch.status === 'FAILED' && !batch.confirmedAt);
}

/** Se reintenta solo si fallo al confirmarse; las filas ya aplicadas no se repiten. */
export function canRetry(batch: ImportBatchLike): boolean {
  return batch.status === 'FAILED' && !!batch.confirmedAt;
}
