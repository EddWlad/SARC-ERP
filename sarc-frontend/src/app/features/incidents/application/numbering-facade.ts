import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { NumberingResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IncidentsApi } from '../data-access/incidents-api';
import { NumberingEnvironment, ticketCode } from '../domain/incident-labels';

/** Mayor numero inicial que acepta el servidor (nueve digitos). */
export const MAX_START_NUMBER = 999_999_999;

/** El numero inicial como entero, o nulo si el texto no es un entero de 1 a 999.999.999. */
export function parseStartNumber(text: string): number | null {
  const trimmed = text.trim();
  if (!/^\d{1,9}$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return value >= 1 && value <= MAX_START_NUMBER ? value : null;
}

/**
 * Numeracion de tickets (FR-013): sin numero inicial configurado no se crean tickets. Un Administrador lo configura
 * una sola vez, antes del primer ticket, confirmando el codigo resultante. La pantalla lee el estado y muestra el
 * mensaje del servidor si algo falla.
 */
@Service({ autoProvided: false })
export class NumberingFacade {
  private readonly api = inject(IncidentsApi);

  /** El estado leido; indefinido mientras no se ha cargado. */
  readonly state = signal<NumberingResponse | undefined>(undefined);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');

  /** El servidor no tiene configurado su ambiente: no se puede configurar ni crear tickets (research R2). */
  readonly serverWithoutEnvironment = computed(() => {
    const current = this.state();
    return current !== undefined && !current.expectedEnvironment;
  });

  /** El ambiente que debe usarse, el del servidor; nulo si falta. */
  readonly expectedEnvironment = computed<NumberingEnvironment | null>(() => {
    const environment = this.state()?.expectedEnvironment;
    return environment === 'PRODUCTION' || environment === 'TEST' ? environment : null;
  });

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.state.set(await firstValueFrom(this.api.numbering()));
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Configura el numero inicial. Devuelve `true` si el servidor lo acepto; si no, deja el mensaje del servidor en
   * `error` y devuelve `false`.
   */
  async configure(startNumber: number, confirmation: string): Promise<boolean> {
    const environment = this.expectedEnvironment();
    if (environment === null) {
      this.error.set('El servidor no tiene configurado el ambiente de la numeración.');
      return false;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      this.state.set(
        await firstValueFrom(
          this.api.configureNumbering({
            startNumber,
            environment,
            confirmation: confirmation.trim(),
          }),
        ),
      );
      return true;
    } catch (error) {
      this.error.set(toProblem(error).detail);
      return false;
    } finally {
      this.saving.set(false);
    }
  }

  /** El codigo que resultaria de un numero inicial, para pedir su confirmacion. */
  codeFor(startNumber: number): string {
    return ticketCode(startNumber);
  }
}
