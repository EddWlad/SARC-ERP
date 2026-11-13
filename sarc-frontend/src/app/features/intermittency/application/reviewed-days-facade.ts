import { inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ReviewedDayMarkResponse, ReviewedDayResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IntermittencyApi } from '../data-access/intermittency-api';
import { addDays, periodRange, todayInOperatingZone } from '../domain/intermittency-time';

/** Sin un mes elegido, el panel muestra los ultimos dias: lo que se revisa al cerrar cada turno. */
const DAYS_SHOWN = 31;

/**
 * Dias revisados de la red KIA (FR-044): un editor marca un dia terminado como revisado y esa marca acredita la
 * cobertura de la disponibilidad estimada. Retirarla pide justificacion. Hoy y los dias futuros no se marcan.
 * Cada accion vuelve a cargar la lista y falla con el mensaje del servidor (409 incluido).
 */
@Service({ autoProvided: false })
export class ReviewedDaysFacade {
  private readonly api = inject(IntermittencyApi);

  private ticket = 0;

  /** Hoy en la zona operativa; se renueva en cada carga para no quedarse en el dia anterior. */
  readonly today = signal(todayInOperatingZone());
  /** aaaa-mm del mes que se revisa; vacio, los ultimos 31 dias. Permite marcar el historico (FR-044). */
  readonly month = signal('');
  /** Del dia mas reciente al mas antiguo. */
  readonly days = signal<readonly ReviewedDayResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  async load(): Promise<void> {
    const ticket = ++this.ticket;
    const today = todayInOperatingZone();
    this.today.set(today);
    this.loading.set(true);
    this.error.set('');
    try {
      const range = this.range(today);
      const days = await firstValueFrom(this.api.reviewedDays(range.from, range.to));
      // Una respuesta tardia de una carga anterior no pisa la actual.
      if (ticket !== this.ticket) return;
      this.days.set([...days].reverse());
    } catch (error) {
      if (ticket !== this.ticket) return;
      this.error.set(toProblem(error).detail);
    } finally {
      if (ticket === this.ticket) this.loading.set(false);
    }
  }

  /**
   * Muestra un mes entero, de cualquier fecha pasada, para marcar el historico. Un mes posterior al vigente no
   * existe todavia: se muestra el vigente.
   */
  async chooseMonth(month: string): Promise<void> {
    const current = todayInOperatingZone().slice(0, 7);
    this.month.set(month > current ? current : month);
    await this.load();
  }

  /** Vuelve a los ultimos 31 dias. */
  async showRecent(): Promise<void> {
    await this.chooseMonth('');
  }

  /** `[from, to)`: el mes elegido hasta hoy incluido, o los ultimos 31 dias. */
  private range(today: string): { from: string; to: string } {
    const tomorrow = addDays(today, 1);
    const month = this.month();
    const first = month
      ? periodRange({ preset: 'FIRST_HALF', month, from: '', lastDay: '', today })
      : null;
    const second = month
      ? periodRange({ preset: 'SECOND_HALF', month, from: '', lastDay: '', today })
      : null;
    if (!first || !second) return { from: addDays(today, -(DAYS_SHOWN - 1)), to: tomorrow };
    return { from: first.from, to: second.to < tomorrow ? second.to : tomorrow };
  }

  /** Marca el dia como revisado; devuelve los lotes sin confirmar que lo tocan (FR-045). */
  mark(day: ReviewedDayResponse): Promise<ReviewedDayMarkResponse> {
    return this.apply(() => firstValueFrom(this.api.markDay(day.day)));
  }

  /** Retira la marca con una justificacion obligatoria. */
  withdraw(day: ReviewedDayResponse, justification: string): Promise<ReviewedDayResponse> {
    return this.apply(() =>
      firstValueFrom(this.api.withdrawDay(day.day, { version: day.version ?? 0, justification })),
    );
  }

  private async apply<T>(request: () => Promise<T>): Promise<T> {
    let result: T;
    try {
      result = await request();
    } catch (error) {
      throw new Error(toProblem(error).detail, { cause: error });
    }
    await this.load();
    return result;
  }
}
