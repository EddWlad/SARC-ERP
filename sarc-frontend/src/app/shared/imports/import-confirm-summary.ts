/**
 * Resumen de lo que hace confirmar un lote, armado con los conteos que trae el propio lote. Cada
 * modulo (consumos, equipos) lo construye desde su respuesta; aqui solo viven la forma y las
 * cuentas derivadas.
 */

/** Como se lee el conteo de un grupo: lo que se aplica, lo que depende de una decision, lo que no. */
export type ImportConfirmTone = 'applies' | 'conditional' | 'untouched';

/** Una clasificacion del lote con su conteo real y, si hace falta, de que depende su destino. */
export interface ImportConfirmItem {
  readonly label: string;
  readonly count: number;
  readonly detail?: string;
}

/** Filas que comparten destino al confirmar. */
export interface ImportConfirmGroup {
  readonly title: string;
  readonly tone: ImportConfirmTone;
  /** Aclaracion del grupo, cuando su destino no depende solo del conteo. */
  readonly note?: string;
  readonly items: readonly ImportConfirmItem[];
}

/** Lo que el analista necesita ver antes de confirmar. Ningun conteo se calcula en pantalla. */
export interface ImportConfirmSummary {
  readonly title: string;
  readonly fileName: string;
  readonly totalRows: number;
  readonly groups: readonly ImportConfirmGroup[];
  /** Conteo que el lote lleva aparte de las clasificaciones (p. ej. lecturas sobre el umbral). */
  readonly aside?: ImportConfirmItem;
  readonly confirmLabel: string;
}

/** Grupos con al menos una clasificacion con filas; dentro, solo las clasificaciones con filas. */
export function visibleGroups(summary: ImportConfirmSummary): ImportConfirmGroup[] {
  return summary.groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.count > 0) }))
    .filter((group) => group.items.length > 0);
}

/** Filas que el resumen tiene clasificadas. El aparte no cuenta: no es una clasificacion. */
export function classifiedRows(summary: ImportConfirmSummary): number {
  return summary.groups.reduce(
    (total, group) => total + group.items.reduce((sum, item) => sum + item.count, 0),
    0,
  );
}

/**
 * Filas del lote que ninguna clasificacion del resumen cubre. Normalmente 0; si el backend agrega
 * una clasificacion que esta pantalla todavia no nombra, el resumen lo dice en vez de cuadrar solo.
 */
export function unclassifiedRows(summary: ImportConfirmSummary): number {
  return Math.max(0, summary.totalRows - classifiedRows(summary));
}
