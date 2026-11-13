import {
  IntermittencyImportBatchResponse,
  IntermittencyImportRowResponse,
  WindowInterpretationResponse,
} from '../../../core/api/generated';
import { ImportConfirmSummary } from '../../../shared/imports/import-confirm-summary';
import { CLASSIFICATION_LABELS, RowClassification } from './intermittency-labels';

export type IntermittencyImportBatch = IntermittencyImportBatchResponse;
export type IntermittencyImportRow = IntermittencyImportRowResponse;
export type RowWindow = WindowInterpretationResponse;
export type RowDecision = NonNullable<IntermittencyImportRow['decision']>;
export type WindowResolution = NonNullable<RowWindow['decision']>;

/** Clasificaciones que la vista previa ofrece como filtro, en el orden en que importan. */
export const IMPORT_CLASSIFICATIONS: readonly RowClassification[] = [
  'INVALID',
  'PENDING_LINK',
  'OUT_OF_SCOPE',
  'CONFLICT',
  'REVIEW',
  'IDENTICAL',
  'NEW',
];

/** Que pasa con las filas de cada clasificacion al confirmar. */
export const IMPORT_CLASSIFICATION_HINTS: Readonly<Record<RowClassification, string>> = {
  NEW: 'Se crean como ventanas confirmadas',
  IDENTICAL: 'Ya registradas con los mismos datos; no se tocan ni se duplican',
  REVIEW: 'Se incorporan; las ventanas largas quedan por revisar',
  CONFLICT: 'Se solapan con otra ventana; elija qué hacer con cada una',
  OUT_OF_SCOPE: 'No son puntos de carga KIA; solo se pueden omitir',
  PENDING_LINK: 'Sin punto resuelto; vincúlelas, déjelas pendientes u omítalas',
  INVALID: 'Datos que no se pudieron interpretar; corríjalos u omita la fila',
};

export const WINDOW_RESOLUTION_LABELS: Readonly<Record<WindowResolution, string>> = {
  KEEP_EXISTING: 'Conservar la registrada',
  REPLACE: 'Reemplazar la registrada',
  MERGE: 'Fusionar con la que se solapa',
  OMIT: 'Omitir esta ventana',
  OPEN: 'Registrar como abierta',
};

export const ROW_DECISION_LABELS: Readonly<Record<RowDecision, string>> = {
  APPLY: 'Incorporar',
  SKIP: 'Omitida',
  KEEP_PENDING: 'Pendiente',
};

export function classificationCount(
  batch: IntermittencyImportBatch,
  classification: RowClassification,
): number {
  const counts: Readonly<Record<RowClassification, number>> = {
    NEW: batch.counts.newRows,
    IDENTICAL: batch.counts.identical,
    CONFLICT: batch.counts.conflict,
    INVALID: batch.counts.invalid,
    PENDING_LINK: batch.counts.pendingLink,
    OUT_OF_SCOPE: batch.counts.outOfScope,
    REVIEW: batch.counts.review,
  };
  return counts[classification];
}

/** Filas que impiden confirmar mientras no se resuelvan. */
export function blockingRows(batch: IntermittencyImportBatch): number {
  const { invalid, conflict, outOfScope, pendingLink } = batch.counts;
  return invalid + conflict + outOfScope + pendingLink;
}

/** Lo que hace confirmar, con los conteos del lote. */
export function importConfirmSummary(batch: IntermittencyImportBatch): ImportConfirmSummary {
  const counts = batch.counts;
  return {
    title: 'Confirmar importación de intermitencias',
    fileName: batch.fileName,
    totalRows: batch.totalRows,
    groups: [
      {
        title: 'Se crean ventanas',
        tone: 'applies',
        note: 'Las ventanas largas quedan por revisar y las marcadas como prueba no cuentan como oficiales.',
        items: [
          { label: CLASSIFICATION_LABELS.NEW, count: counts.newRows },
          { label: CLASSIFICATION_LABELS.REVIEW, count: counts.review },
        ],
      },
      {
        title: 'Dependen de lo que decidió',
        tone: 'conditional',
        items: [
          {
            label: CLASSIFICATION_LABELS.CONFLICT,
            count: counts.conflict,
            detail: 'Solo se reemplazan las ya registradas',
          },
          {
            label: CLASSIFICATION_LABELS.PENDING_LINK,
            count: counts.pendingLink,
            detail: 'Las pendientes no crean ventanas',
          },
        ],
      },
      {
        title: 'No se tocan',
        tone: 'untouched',
        items: [
          { label: CLASSIFICATION_LABELS.IDENTICAL, count: counts.identical },
          { label: 'Omitidas', count: counts.skipped },
          { label: CLASSIFICATION_LABELS.OUT_OF_SCOPE, count: counts.outOfScope },
          { label: CLASSIFICATION_LABELS.INVALID, count: counts.invalid },
        ],
      },
    ],
    confirmLabel: 'Confirmar importación',
  };
}
