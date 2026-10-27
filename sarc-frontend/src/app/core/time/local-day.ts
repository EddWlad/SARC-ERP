import { DOCUMENT } from '@angular/common';
import { inject, Service, signal } from '@angular/core';

/**
 * Zona en que opera el negocio (Ecuador continental, sin horario de verano). Los consumos se
 * cuentan el dia en que inician en esta zona, sea cual sea la zona del navegador.
 */
const OPERATING_TIME_ZONE = 'America/Guayaquil';
/** Desplazamiento fijo de esa zona, para `DatePipe` (que solo acepta desplazamientos). */
export const OPERATING_UTC_OFFSET = '-0500';

const DAY_MS = 24 * 60 * 60 * 1000;
const DAY_PARTS = new Intl.DateTimeFormat('en-CA', {
  timeZone: OPERATING_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/**
 * Dia de operacion (Guayaquil) como signal. Cambia a medianoche de esa zona y al volver a la
 * pestaña, para que una pantalla abierta de un dia para otro pida "hoy" de nuevo. El valor es una
 * fecha local con el año, mes y dia de Guayaquil: asi se usa igual que una fecha del calendario.
 */
@Service()
export class LocalDay {
  private readonly current = signal(operatingToday());
  readonly today = this.current.asReadonly();

  constructor() {
    const document = inject(DOCUMENT);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        this.refresh();
      }
    });
    this.scheduleMidnight();
  }

  private scheduleMidnight(): void {
    const { hour, minute, second } = operatingParts(new Date());
    const elapsed = ((hour * 60 + minute) * 60 + second) * 1000;
    setTimeout(
      () => {
        this.refresh();
        this.scheduleMidnight();
      },
      DAY_MS - elapsed + 1000,
    );
  }

  private refresh(): void {
    const day = operatingToday();
    if (day.getTime() !== this.current().getTime()) {
      this.current.set(day);
    }
  }
}

/** Año, mes, dia y hora actuales en la zona de operacion. */
export function operatingParts(
  instant: Date,
): Record<'year' | 'month' | 'day' | 'hour' | 'minute' | 'second', number> {
  const parts = Object.fromEntries(
    DAY_PARTS.formatToParts(instant)
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, Number(part.value)]),
  );
  return {
    year: parts['year'],
    month: parts['month'],
    day: parts['day'],
    hour: parts['hour'],
    minute: parts['minute'],
    second: parts['second'],
  };
}

function operatingToday(): Date {
  const { year, month, day } = operatingParts(new Date());
  return new Date(year, month - 1, day);
}
