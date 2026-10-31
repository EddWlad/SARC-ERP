import {
  ImportBatchResponse,
  ImportRowResponse,
  PageResultImportBatchResponse,
  PageResultImportRowResponse,
} from '../../../core/api/generated';
import { ImportConfirmSummary } from '../../../shared/imports/import-confirm-summary';
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
  LINKABLE: 'Se vinculan',
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
  LINKABLE: 'Sesiones pendientes cuya identidad ya resuelve un equipo; se vinculan al confirmar',
  INVALID: 'No se pudieron interpretar; no se incorporan',
  DUPLICATE: 'Repiten una sesión anterior del mismo lote; se ignoran',
  PENDING_LINK: 'Se guardan sin equipo hasta vincularlas; no suman',
};

export const CLASSIFICATIONS = Object.keys(CLASSIFICATION_LABELS) as readonly RowClassification[];

export const OUTCOME_LABELS: Readonly<Record<RowOutcome, string>> = {
  INSERTED: 'Incorporada',
  CORRECTED: 'Corregida',
  REASSIGNED: 'Reasignada',
  LINKED: 'Vinculada',
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
    case 'LINKABLE':
      return batch.linkableRows;
    case 'INVALID':
      return batch.invalidRows;
    case 'DUPLICATE':
      return batch.duplicateRows;
    case 'PENDING_LINK':
      return batch.pendingLinkRows;
  }
}

/**
 * Que hace confirmar este lote, agrupado por destino y con los conteos que trae el lote. El total
 * de filas aprobadas o reasignadas no esta en la respuesta (es una decision por fila y solo se
 * cargan las de la pagina visible): el grupo condicional lo dice en vez de estimarlo.
 */
export function sessionConfirmSummary(batch: ImportBatch): ImportConfirmSummary {
  return {
    title: 'Confirmar importación de sesiones',
    fileName: batch.fileName,
    totalRows: batch.totalRows,
    confirmLabel: 'Confirmar importación',
    groups: [
      {
        title: 'Se incorporan al confirmar',
        tone: 'applies',
        items: [
          {
            label: CLASSIFICATION_LABELS.NEW,
            count: batch.newRows,
            detail: 'sesiones que todavía no estaban importadas',
          },
          {
            label: CLASSIFICATION_LABELS.LINKABLE,
            count: batch.linkableRows,
            detail: 'pendientes cuya identidad ya resuelve un equipo',
          },
        ],
      },
      {
        title: 'Solo las que haya decidido fila por fila',
        note: 'El lote no lleva el total de decisiones tomadas: lo que no haya aprobado o reasignado se omite.',
        tone: 'conditional',
        items: [
          {
            label: CLASSIFICATION_LABELS.MODIFIED,
            count: batch.modifiedRows,
            detail: 'se corrigen solo si las aprobó',
          },
          {
            label: CLASSIFICATION_LABELS.EQUIPMENT_CHANGED,
            count: batch.equipmentChangedRows,
            detail: 'se mueven de cargador solo si las reasignó',
          },
        ],
      },
      {
        title: 'Se guardan sin sumar a los indicadores',
        tone: 'conditional',
        items: [
          {
            label: CLASSIFICATION_LABELS.PENDING_LINK,
            count: batch.pendingLinkRows,
            detail: 'quedan sin equipo hasta que alguien las vincule',
          },
        ],
      },
      {
        title: 'No se tocan',
        tone: 'untouched',
        items: [
          { label: CLASSIFICATION_LABELS.IDENTICAL, count: batch.identicalRows },
          { label: CLASSIFICATION_LABELS.DUPLICATE, count: batch.duplicateRows },
          {
            label: CLASSIFICATION_LABELS.INVALID,
            count: batch.invalidRows,
            detail: 'no se pudieron interpretar',
          },
        ],
      },
    ],
    aside: {
      label: 'Lecturas superiores a 1.000 kWh',
      count: batch.energyReviewRows,
      detail:
        'conteo aparte de las clasificaciones; se guardan por revisar y no suman hasta que alguien las decida',
    },
  };
}

/** Columnas que el analista debe ver en el reporte original de cada fuente (ayuda en pantalla). */
export const SOURCE_COLUMNS: Readonly<Record<SessionSource, string>> = {
  WALLBOX: 'Serial number, Charger, Session id, Start, End, Energy (kWh)',
  ENERLINK: 'ID, INICIO, TÉRMINO, ACTIVO, CARGADOR, CONECTOR, ENERGÍA CARGADA (el resto se ignora)',
};
