/** Zona operativa: America/Guayaquil, UTC-5 todo el año (no tiene horario de verano). */
export const OPERATING_TIME_ZONE = 'America/Guayaquil';

/** Texto de un dato sin valor: nunca se muestra como cero (FR-042). */
export const NO_VALUE = '—';

/**
 * Duración en horas acumuladas y minutos, por ejemplo «1:49» o «36:42». Un valor nulo o indefinido es «—», nunca
 * 0: un hito sin hora no es una duración de cero minutos. Un 0 real (T1 a la misma hora de T0) sí es «0:00».
 */
export function durationText(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined || Number.isNaN(minutes)) return NO_VALUE;
  const total = Math.max(0, Math.floor(minutes));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

/** Fecha y hora de un instante en la zona operativa. */
export function localParts(instant: string | Date): { date: string; time: string } {
  const text = new Intl.DateTimeFormat('sv-SE', {
    timeZone: OPERATING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(instant));
  const [date, time] = text.split(' ');
  return { date, time };
}

/** Un instante como «aaaa-mm-dd hh:mm» en la zona operativa; «—» si falta. */
export function formatInstant(instant: string | Date | null | undefined): string {
  if (instant === null || instant === undefined || instant === '') return NO_VALUE;
  const { date, time } = localParts(instant);
  return `${date} ${time}`;
}

/** Minutos enteros transcurridos entre dos instantes, redondeados hacia abajo; nulo si falta alguno. */
export function elapsedMinutes(
  from: string | Date | null | undefined,
  to: string | Date | null | undefined,
): number | null {
  if (!from || !to) return null;
  const diff = (new Date(to).getTime() - new Date(from).getTime()) / 60000;
  return Number.isFinite(diff) ? Math.floor(diff) : null;
}

/**
 * Antigüedad de un ticket abierto como «2 d 3:15» o «3:15»: el tiempo transcurrido desde T0 hasta ahora. Los días
 * solo aparecen cuando hay al menos uno.
 */
export function ageText(t0: string | Date | null | undefined, now: Date = new Date()): string {
  const minutes = elapsedMinutes(t0, now);
  if (minutes === null || minutes < 0) return NO_VALUE;
  const days = Math.floor(minutes / 1440);
  const rest = durationText(minutes - days * 1440);
  return days > 0 ? `${days} d ${rest}` : rest;
}

/** America/Guayaquil es UTC-5 todo el año. */
const OPERATING_OFFSET = '-05:00';

/** Una hora anterior a ahora en menos de este margen no se considera retroactiva (FR-018). */
export const RETROACTIVE_TOLERANCE_MS = 60_000;

/** El instante ISO de un `datetime-local` escrito en la zona operativa; nulo si está vacío o es inválido. */
export function operatingInstant(local: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local)) return null;
  const date = new Date(`${local}:00${OPERATING_OFFSET}`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** Si un `datetime-local` es más de un minuto anterior a ahora: exige un motivo. Vacío no es retroactivo. */
export function isRetroactiveLocal(local: string, now: Date = new Date()): boolean {
  const instant = operatingInstant(local);
  return instant !== null && new Date(instant).getTime() < now.getTime() - RETROACTIVE_TOLERANCE_MS;
}

/** Un instante como `aaaa-mm-ddThh:mm` en la zona operativa, para precargar un `datetime-local`; vacío si falta. */
export function localInput(instant: string | Date | null | undefined): string {
  if (instant === null || instant === undefined || instant === '') return '';
  const { date, time } = localParts(instant);
  return `${date}T${time}`;
}
