import { AvailabilityResponse } from '../../../core/api/generated';

export type Availability = AvailabilityResponse;
export type AvailabilityPoint = Availability['points'][number];
export type ExclusionReason = Availability['exclusions'][number]['reason'];

/** Por que un punto queda fuera de la disponibilidad estimada (FR-045). */
export const EXCLUSION_LABELS: Readonly<Record<ExclusionReason, string>> = {
  DISCONNECTED: 'Desconectado: instalado sin servicio',
  INACTIVE: 'Inactivo y sin ventanas en el período',
  DELETED: 'Eliminado y sin ventanas en el período',
};

const PERCENT = new Intl.NumberFormat('es-EC', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Disponibilidad con dos decimales; sin tiempo evaluado es «Sin datos», nunca 100 % (FR-046). */
export function availabilityText(percent: number | null | undefined): string {
  return percent === null || percent === undefined ? 'Sin datos' : `${PERCENT.format(percent)} %`;
}

/** Dias cubiertos sobre los dias en alcance: los del rango ya terminados hasta el corte (FR-046). */
export function coverageText(coveredDays: number, daysInScope: number): string {
  return `${coveredDays} de ${daysInScope} ${daysInScope === 1 ? 'día' : 'días'}`;
}
