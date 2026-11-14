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

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}
