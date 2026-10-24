import { InventorySummaryResponse } from '../../../core/api/generated';

export type InventorySummary = InventorySummaryResponse;

/** Cantidad de una categoria del resumen; el backend completa con ceros, esto cubre claves ausentes. */
export function countOf(counts: Readonly<Record<string, number>>, key: string): number {
  return counts[key] ?? 0;
}
