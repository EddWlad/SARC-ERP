import { WindowResponse } from '../../../core/api/generated';

export type WindowState = WindowResponse['state'];
export type ReviewReason = WindowResponse['reviewReasons'][number];

/** Estados de una ventana. «Pendiente de vincular» es la clasificacion de una fila de lote, no de una ventana. */
export const STATE_LABELS: Readonly<Record<WindowState, string>> = {
  CONFIRMED: 'Confirmada',
  OPEN: 'Abierta',
  REVIEW: 'Por revisar',
  TEST: 'Prueba',
  ANNULLED: 'Anulada',
};

export const PENDING_LINK_LABEL = 'Pendiente de vincular';

/** Clasificaciones de una fila de lote (FR-025). */
export type RowClassification =
  'NEW' | 'IDENTICAL' | 'CONFLICT' | 'INVALID' | 'PENDING_LINK' | 'OUT_OF_SCOPE' | 'REVIEW';

export const CLASSIFICATION_LABELS: Readonly<Record<RowClassification, string>> = {
  NEW: 'Nueva',
  // La «Duplicada» de FR-025: ya existe una ventana idéntica guardada.
  IDENTICAL: 'Duplicada (ya registrada)',
  CONFLICT: 'En conflicto',
  INVALID: 'Inválida',
  PENDING_LINK: PENDING_LINK_LABEL,
  OUT_OF_SCOPE: 'Fuera de alcance',
  REVIEW: 'Por revisar',
};

/** Motivos que acompanan a una fila o a una ventana. Los codigos son los de data-model.md. */
export const REASON_LABELS: Readonly<Record<string, string>> = {
  ALREADY_RECORDED: 'Ya existe una ventana idéntica',
  OVERLAPS_EXISTING: 'Se solapa con una ventana ya registrada',
  OVERLAP_IN_BATCH: 'Se solapa con otra ventana del archivo',
  // Duplicada dentro del mismo lote, dentro de «En conflicto».
  DUPLICATE_IN_BATCH: 'Duplicada en el lote',
  DATE_FORMAT: 'La fecha no es válida',
  TIME_FORMAT: 'La hora no es válida',
  SECONDS_AMBIGUOUS: 'La hora tiene segundos que no se pueden redondear al minuto',
  END_BEFORE_START: 'El fin es anterior o igual al inicio',
  ZERO_DURATION: 'El inicio y el fin son iguales',
  INCOMPLETE_PAIR: 'Falta el inicio o el fin',
  FUTURE_INSTANT: 'Es posterior al minuto en curso',
  UNKNOWN_CODE: 'No hay un punto con ese código',
  AMBIGUOUS_CODE: 'Más de un punto tiene ese código',
  DELETED_EQUIPMENT: 'El punto está eliminado',
  MISSING_CODE: 'La fila no trae código',
  NOT_KIA_CHARGING_POINT: 'No es un punto de carga KIA',
  TOTAL_MISMATCH: 'El total del archivo no coincide con sus ventanas',
  LONG_WINDOW: 'Dura 4 horas o más',
  NAME_DIFFERS: 'El nombre del archivo difiere del actual',
  INACTIVE_EQUIPMENT: 'El punto está inactivo',
  TOTAL_MISSING: 'La fila no trae total inactivo',
};

export function reasonLabel(code: string): string {
  return REASON_LABELS[code] ?? code;
}

/** Franjas de duracion del grafico de pastel (FR-036): los limites se incluyen en la franja de arriba. */
export type DurationBand =
  'UNDER_5' | 'FROM_5_TO_14' | 'FROM_15_TO_59' | 'FROM_60_TO_239' | 'FROM_240';

export const DURATION_BAND_LABELS: Readonly<Record<DurationBand, string>> = {
  UNDER_5: 'Menos de 5 min',
  FROM_5_TO_14: '5 a 14 min',
  FROM_15_TO_59: '15 a 59 min',
  FROM_60_TO_239: '1 h a menos de 4 h',
  FROM_240: '4 h o más',
};

/** Origen de una ventana. */
export const SOURCE_LABELS: Readonly<Record<WindowResponse['source'], string>> = {
  MANUAL: 'Captura manual',
  XLSX: 'Importación XLSX',
  CSV: 'Importación CSV',
  PASTE: 'Importación pegada',
};

/** Cambios que registra el historial de revisiones de una ventana (FR-017). */
export const REVISION_CHANGE_LABELS: Readonly<Record<string, string>> = {
  CREATED: 'Registrada',
  CORRECTED: 'Corregida',
  CLOSED: 'Fin registrado',
  REVIEW_CONFIRMED: 'Revisión confirmada',
  MARKED_TEST: 'Marcada como prueba',
  UNMARKED_TEST: 'Prueba quitada',
  ANNULLED: 'Anulada',
  REPLACED_BY_IMPORT: 'Reemplazada por una importación',
};

export function revisionChangeLabel(code: string): string {
  return REVISION_CHANGE_LABELS[code] ?? code;
}

/** Clase de la insignia de cada estado (`shared/ui/_badges.scss`). */
export const STATE_BADGES: Readonly<Record<WindowState, string>> = {
  CONFIRMED: 'badge',
  OPEN: 'badge badge-accent',
  REVIEW: 'badge badge-warn',
  TEST: 'badge badge-info',
  ANNULLED: 'badge badge-off',
};
