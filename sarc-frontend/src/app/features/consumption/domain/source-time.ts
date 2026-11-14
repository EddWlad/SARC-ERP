import { OPERATING_UTC_OFFSET } from '../../../core/time/local-day';

/**
 * Hora de la fuente (Feature 009, FR-002): cada sesion se muestra con el desplazamiento UTC con que la
 * escribio su archivo, el mismo que fija su fecha de conteo. Sin desplazamiento conocido (historico sin
 * evidencia) se muestra en la zona de operacion, como antes.
 */
export function sourceUtcOffset(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) {
    return OPERATING_UTC_OFFSET;
  }
  const sign = minutes < 0 ? '-' : '+';
  const absolute = Math.abs(minutes);
  return sign + pad(Math.floor(absolute / 60)) + pad(absolute % 60);
}

/** Etiqueta visible solo cuando la fuente no escribe en hora de Ecuador, p. ej. «UTC+00:00». */
export function sourceUtcLabel(minutes: number | null | undefined): string | null {
  const offset = sourceUtcOffset(minutes);
  return offset === OPERATING_UTC_OFFSET ? null : `UTC${offset.slice(0, 3)}:${offset.slice(3)}`;
}

/** Formatos de la hora de la fuente: con los segundos que informa el archivo (FR-002). */
export const SOURCE_DATE_TIME = 'dd/MM/yyyy HH:mm:ss';
export const SOURCE_TIME = 'HH:mm:ss';

/** Desplazamiento del fin: el propio si la fuente lo declaro distinto; si no, el del inicio. */
export function sourceEndUtcOffset(
  start: number | null | undefined,
  end: number | null | undefined,
): string {
  return sourceUtcOffset(end ?? start);
}

/** Etiqueta del fin solo cuando su desplazamiento difiere del inicio, p. ej. «UTC-05:00». */
export function sourceEndUtcLabel(
  start: number | null | undefined,
  end: number | null | undefined,
): string | null {
  if (end === null || end === undefined || end === start) {
    return null;
  }
  const offset = sourceUtcOffset(end);
  return `UTC${offset.slice(0, 3)}:${offset.slice(3)}`;
}

/**
 * Fecha y hora local con que la fuente escribio un instante, para un `datetime-local` con segundos
 * (`2026-11-11T00:30:00`). Se calcula con el desplazamiento, sin depender de la zona del navegador.
 */
export function sourceLocalInput(instant: string, minutes: number | null | undefined): string {
  const offset = minutesOf(sourceUtcOffset(minutes));
  return new Date(Date.parse(instant) + offset * 60_000).toISOString().slice(0, 19);
}

/** Instante ISO con el desplazamiento de origen a partir de una hora local escrita en esa fuente. */
export function withSourceOffset(local: string, minutes: number | null | undefined): string {
  const offset = sourceUtcOffset(minutes);
  const seconds = local.length === 16 ? `${local}:00` : local.slice(0, 19);
  return `${seconds}${offset.slice(0, 3)}:${offset.slice(3)}`;
}

function minutesOf(offset: string): number {
  const sign = offset.startsWith('-') ? -1 : 1;
  return sign * (Number(offset.slice(1, 3)) * 60 + Number(offset.slice(3, 5)));
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}
