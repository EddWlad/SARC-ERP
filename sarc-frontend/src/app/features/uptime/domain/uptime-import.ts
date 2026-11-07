import {
  ConnectorAliasResponse,
  UptimeImportBatchResponse,
  UptimeImportRowResponse,
} from '../../../core/api/generated';
import { ImportConfirmSummary } from '../../../shared/imports/import-confirm-summary';

export type UptimeImportBatch = UptimeImportBatchResponse;
export type UptimeImportRow = UptimeImportRowResponse;
export type ConnectorAlias = ConnectorAliasResponse;
export type UptimeRowClassification = UptimeImportRow['classification'];
export type UptimeRowOutcome = NonNullable<UptimeImportRow['outcome']>;
export type DeclaredPeriodType = 'ROLLING_24H' | 'ROLLING_7D' | 'ROLLING_30D';

/** Incluir u omitir; incluir un conflicto exige justificacion. */
export interface UptimeRowDecision {
  readonly approved: boolean;
  readonly justification?: string;
}

export const DECLARED_PERIOD_TYPES: readonly DeclaredPeriodType[] = [
  'ROLLING_24H',
  'ROLLING_7D',
  'ROLLING_30D',
];

export const UPTIME_CLASSIFICATION_LABELS: Readonly<Record<UptimeRowClassification, string>> = {
  NEW: 'Nuevas',
  IDENTICAL: 'Idénticas',
  CONFLICT: 'En conflicto',
  INVALID: 'Inválidas',
  PENDING_LINK: 'Pendientes de vincular',
};

export const UPTIME_CLASSIFICATION_HINTS: Readonly<Record<UptimeRowClassification, string>> = {
  NEW: 'Quedan pendientes de validar al confirmar',
  IDENTICAL: 'Ya registradas con los mismos datos; no se tocan',
  CONFLICT: 'Difieren de lo registrado; se corrigen solo con justificación',
  INVALID: 'No se pudieron interpretar; no se incorporan',
  PENDING_LINK: 'Sin conector resuelto; quedan fuera de los indicadores hasta vincularlas',
};

export const UPTIME_CLASSIFICATIONS = Object.keys(
  UPTIME_CLASSIFICATION_LABELS,
) as readonly UptimeRowClassification[];

export const UPTIME_OUTCOME_LABELS: Readonly<Record<UptimeRowOutcome, string>> = {
  APPLIED: 'Incorporada',
  SKIPPED: 'Omitida',
  FAILED: 'No aplicada',
  PENDING_LINK: 'Por vincular',
  LINKED: 'Vinculada',
};

export function uptimeClassificationCount(
  batch: UptimeImportBatch,
  classification: UptimeRowClassification,
): number {
  const counts: Readonly<Record<UptimeRowClassification, number>> = {
    NEW: batch.newRows,
    IDENTICAL: batch.identicalRows,
    CONFLICT: batch.conflictRows,
    INVALID: batch.invalidRows,
    PENDING_LINK: batch.pendingLinkRows,
  };
  return counts[classification];
}

/** Lo que hace confirmar, con los conteos del lote. */
export function uptimeConfirmSummary(batch: UptimeImportBatch): ImportConfirmSummary {
  return {
    title: 'Confirmar importación de uptime',
    fileName: batch.fileName,
    totalRows: batch.totalRows,
    groups: [
      {
        title: 'Se incorporan',
        tone: 'applies',
        items: [{ label: 'Nuevas', count: batch.newRows, detail: 'Las omitidas no entran' }],
      },
      {
        title: 'Dependen de tu decisión',
        tone: 'conditional',
        note: 'Sin justificación, un conflicto no cambia la medición existente.',
        items: [{ label: 'En conflicto', count: batch.conflictRows }],
      },
      {
        title: 'No se tocan',
        tone: 'untouched',
        items: [
          { label: 'Idénticas', count: batch.identicalRows },
          { label: 'Inválidas', count: batch.invalidRows },
          { label: 'Pendientes de vincular', count: batch.pendingLinkRows },
        ],
      },
    ],
    confirmLabel: 'Confirmar importación',
  };
}

/** Semana (lunes a domingo) que puede reportar el informe semanal. */
export interface ReportWeek {
  /** Lunes en aaaa-mm-dd, como lo pide la API. */
  readonly value: string;
  readonly label: string;
}

/** Las ultimas semanas ya terminadas, la mas reciente primero. */
export function reportWeeks(today: Date, count = 8): readonly ReportWeek[] {
  const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7) - 7);
  return Array.from({ length: count }, (_, index) => {
    const start = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() - 7 * index);
    const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6);
    return { value: isoDate(start), label: `${dayLabel(start)} – ${dayLabel(end)}` };
  });
}

function isoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function dayLabel(date: Date): string {
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Contexto del informe, nunca intervalo observado. */
export function uptimeTemporalLabel(
  batch: Pick<UptimeImportBatch, 'temporalPrecision' | 'referenceWeek'>,
): string {
  if (batch.temporalPrecision === 'REFERENCE_WEEK') {
    return (
      'Horario real no informado' +
      (batch.referenceWeek ? ' · Referencia ' + batch.referenceWeek : '')
    );
  }
  return batch.temporalPrecision === 'KNOWN_RANGE'
    ? 'Con horario real'
    : 'Procedencia temporal por conciliar';
}
export function uptimeCanConfirmExact(
  batch: Pick<UptimeImportBatch, 'temporalPrecision'>,
): boolean {
  return batch.temporalPrecision === 'KNOWN_RANGE';
}
