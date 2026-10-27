import { IssuedReportResponse, PageResultIssuedReportResponse } from '../../../core/api/generated';

/** Contrato del backend (RF-RPT-08): cada reporte emitido con su alcance y huella. */
export type IssuedReport = IssuedReportResponse;
export type IssuedReportPage = PageResultIssuedReportResponse;

/** Tipos de reporte que se archivan; el backend usa el modulo que lo emitio. */
export type ReportKind = 'CONSUMPTION' | 'INVENTORY';

export const KIND_LABELS: Readonly<Record<ReportKind, string>> = {
  CONSUMPTION: 'Consumo',
  INVENTORY: 'Inventario',
};

export const REPORT_KINDS = Object.keys(KIND_LABELS) as readonly ReportKind[];

/** Etiqueta del tipo; uno desconocido (de un modulo futuro) se muestra tal cual. */
export function kindLabel(kind: string): string {
  return kind in KIND_LABELS ? KIND_LABELS[kind as ReportKind] : kind;
}
