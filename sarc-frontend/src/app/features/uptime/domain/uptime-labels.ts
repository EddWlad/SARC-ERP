export type PeriodType = 'ROLLING_24H' | 'ROLLING_7D' | 'ROLLING_30D' | 'EXACT_WEEK';
export type ReadingClass = 'MEASURED' | 'NO_DATA' | 'OUT_OF_SCOPE' | 'RETIRED';
export type ReviewState = 'PENDING' | 'VALIDATED' | 'SUPERSEDED';
export type MeasurementQueue = 'PENDING' | 'OBSERVED';
export type SkipReason = 'OBSERVED' | 'STALE_VERSION' | 'NOT_PENDING' | 'NOT_FOUND';
export type ObservationCode =
  | 'SUM_BELOW_DURATION'
  | 'PERCENT_MISMATCH'
  | 'SUM_ABOVE_DURATION'
  | 'SUM_ZERO'
  | 'PERCENT_OUT_OF_RANGE'
  | 'DURATION_INVALID'
  | 'WINDOW_DURATION_MISMATCH';

export const PERIOD_TYPE_LABELS: Readonly<Record<PeriodType, string>> = {
  ROLLING_24H: 'Últimas 24 horas (provisional)',
  ROLLING_7D: 'Últimos 7 días (provisional)',
  ROLLING_30D: 'Últimos 30 días (provisional)',
  EXACT_WEEK: 'Semana exacta',
};

export const READING_CLASS_LABELS: Readonly<Record<ReadingClass, string>> = {
  MEASURED: 'Medido',
  NO_DATA: 'Sin datos',
  OUT_OF_SCOPE: 'Fuera de alcance',
  RETIRED: 'Retirado',
};

export const REVIEW_STATE_LABELS: Readonly<Record<ReviewState, string>> = {
  PENDING: 'Pendiente',
  VALIDATED: 'Validada',
  SUPERSEDED: 'Reemplazada',
};

export const SKIP_REASON_LABELS: Readonly<Record<SkipReason, string>> = {
  OBSERVED: 'Está observada: se valida una a una',
  STALE_VERSION: 'Otra persona la modificó',
  NOT_PENDING: 'Ya no está pendiente',
  NOT_FOUND: 'Ya no existe',
};

export const OBSERVATION_LABELS: Readonly<Record<ObservationCode, string>> = {
  SUM_BELOW_DURATION: 'La suma de estados es menor que el tiempo evaluado',
  PERCENT_MISMATCH: 'El porcentaje de Enerlink difiere del calculado',
  SUM_ABOVE_DURATION: 'La suma de estados supera el tiempo evaluado',
  SUM_ZERO: 'La suma de estados es cero',
  PERCENT_OUT_OF_RANGE: 'El porcentaje está fuera de 0 a 100',
  DURATION_INVALID: 'La duración no se puede expresar en minutos enteros',
  WINDOW_DURATION_MISMATCH: 'La ventana no coincide con su duración nominal',
};
