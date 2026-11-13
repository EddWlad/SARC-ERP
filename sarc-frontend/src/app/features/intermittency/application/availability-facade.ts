import { httpResource } from '@angular/common/http';
import { computed, inject, Service } from '@angular/core';

import { SessionStore } from '../../../core/auth/session-store';
import { refreshWhileVisible } from '../../../core/http/auto-refresh';
import { retainedValue } from '../../../core/http/retained-value';
import { toProblem } from '../../../core/http/problem';
import { IntermittencyApi } from '../data-access/intermittency-api';
import { Availability } from '../domain/intermittency-availability';
import { INTERMITTENCY_AVAILABILITY } from '../domain/intermittency-permissions';
import { DashboardFacade } from './dashboard-facade';

/**
 * Disponibilidad estimada de prueba para el periodo y los puntos del tablero (FR-042 a FR-046a). Solo se pide si
 * el usuario tiene el permiso: sin el, ni siquiera se crea la peticion (FR-006). El servidor lo vuelve a
 * comprobar en cada consulta.
 */
@Service({ autoProvided: false })
export class AvailabilityFacade {
  private readonly api = inject(IntermittencyApi);
  private readonly dashboard = inject(DashboardFacade);

  readonly allowed = inject(SessionStore).hasAnyRole(INTERMITTENCY_AVAILABILITY);

  private readonly result = httpResource<Availability>(() => {
    if (!this.allowed) return undefined;
    const query = this.dashboard.query();
    return query ? this.api.availabilityRequest(query) : undefined;
  });

  private readonly retained = retainedValue(this.result);
  /** Sin un rango consultable no hay cifras: las del periodo anterior se leerian como las del nuevo. */
  readonly data = computed(() => (this.dashboard.range() === null ? undefined : this.retained()));
  readonly loading = this.result.isLoading;
  readonly error = computed(() => {
    const error = this.result.error();
    return error === undefined ? '' : toProblem(error).detail;
  });

  constructor() {
    // Los dias revisados los marcan tambien otros analistas: se actualiza sola mientras se mira.
    refreshWhileVisible(() => this.reload());
  }

  reload(): void {
    this.result.reload();
  }
}
