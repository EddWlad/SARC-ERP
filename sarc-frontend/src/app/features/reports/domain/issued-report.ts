import { IssuedReportResponse, PageResultIssuedReportResponse } from '../../../core/api/generated';

/** Contrato del backend (RF-RPT-08): cada reporte emitido con su alcance y huella. */
export type IssuedReport = IssuedReportResponse;
export type IssuedReportPage = PageResultIssuedReportResponse;

/** Tipos de reporte que se archivan; el backend usa el modulo que lo emitio. */
export type ReportKind = 'CONSUMPTION' | 'INVENTORY' | 'UPTIME';

export const KIND_LABELS: Readonly<Record<ReportKind, string>> = {
  CONSUMPTION: 'Consumo',
  INVENTORY: 'Inventario',
  UPTIME: 'Uptime',
};

export const REPORT_KINDS = Object.keys(KIND_LABELS) as readonly ReportKind[];

/** Etiqueta del tipo; uno desconocido (de un modulo futuro) se muestra tal cual. */
export function kindLabel(kind: string): string {
  return kind in KIND_LABELS ? KIND_LABELS[kind as ReportKind] : kind;
}

/** Limite del backend para un reporte de consumo. */
export const REPORT_MAX_DAYS = 366;
/** Zona operativa de los criterios de un reporte (D13). */
export const REPORT_TIME_ZONE = 'America/Guayaquil';

export type ReportSource = 'ENERLINK' | 'WALLBOX';

/** Propio de Reportes: el vocabulario de Consumo es interno de ese modulo. */
export const REPORT_SOURCE_LABELS: Readonly<Record<ReportSource, string>> = {
  ENERLINK: 'Enerlink',
  WALLBOX: 'Wallbox',
};

export const REPORT_SOURCES = Object.keys(REPORT_SOURCE_LABELS) as readonly ReportSource[];

/** Filtros del contrato HTTP; Reportes no importa el dominio privado de Consumo. */
export interface ManagerialReportCriteria {
  readonly from: string;
  readonly to: string;
  readonly timeZone: string;
  readonly network?: string;
  readonly type?: string;
  readonly source?: string;
  readonly equipmentId?: string;
}

export interface ReportEquipmentOption {
  readonly id: string;
  readonly name: string;
  readonly assignedCode?: string | null;
}

/** Identidad propia de una emision nueva, tomada de sus cabeceras. */
export interface GeneratedReportInfo {
  readonly id: string | null;
  readonly cutOff: string | null;
}

const DAY_MS = 86_400_000;

function utcDay(day: string): number {
  return new Date(`${day}T00:00:00Z`).getTime();
}

export function shiftReportDay(day: string, amount: number): string {
  const time = utcDay(day);
  return Number.isNaN(time) ? '' : new Date(time + amount * DAY_MS).toISOString().slice(0, 10);
}

/** Dias de un periodo con ambos extremos incluidos; null si falta una fecha. */
export function reportDayCount(from: string, to: string): number | null {
  const days = (utcDay(to) - utcDay(from)) / DAY_MS;
  return Number.isNaN(days) ? null : days + 1;
}
