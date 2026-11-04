import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';

import {
  CorrectMeasurementRequest,
  MeasurementDetailResponse,
  MeasurementHistoryResponse,
} from '../../../core/api/generated';
import { SessionStore } from '../../../core/auth/session-store';
import { toProblem } from '../../../core/http/problem';
import { UptimeApi } from '../data-access/uptime-api';
import { UPTIME_EDITORS } from '../domain/uptime-permissions';

/** Detalle e historial de una medicion. Toda accion viaja con la version leida. */
@Injectable()
export class MeasurementFacade {
  private readonly api = inject(UptimeApi);
  readonly canEdit = inject(SessionStore).hasAnyRole(UPTIME_EDITORS);

  readonly detail = signal<MeasurementDetailResponse | null>(null);
  readonly history = signal<MeasurementHistoryResponse | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly effective = computed(() => {
    const effective = this.detail()?.effectiveRevision;
    return this.history()?.revisions.find((item) => item.revision === effective) ?? null;
  });

  async load(id: string): Promise<void> {
    this.loading.set(true);
    try {
      this.detail.set(await firstValueFrom(this.api.detail(id)));
      if (this.canEdit) this.history.set(await firstValueFrom(this.api.revisions(id)));
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  validate(justification?: string): Promise<boolean> {
    return this.act((id, version) => this.api.validate(id, { version, justification }));
  }

  correct(request: Omit<CorrectMeasurementRequest, 'version'>): Promise<boolean> {
    return this.act((id, version) => this.api.correct(id, { ...request, version }));
  }

  exclude(justification: string): Promise<boolean> {
    return this.act((id, version) => this.api.exclude(id, { version, justification }));
  }

  restore(justification: string): Promise<boolean> {
    return this.act((id, version) => this.api.restore(id, { version, justification }));
  }

  /** Tras un rechazo recarga: otra persona pudo cambiarla y la version debe ser la vigente. */
  private async act(call: (id: string, version: number) => Observable<unknown>): Promise<boolean> {
    const detail = this.detail();
    if (!detail) return false;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(call(detail.id, detail.version));
      await this.load(detail.id);
      return true;
    } catch (error) {
      const problem = toProblem(error);
      if (problem.status === 409) await this.load(detail.id);
      this.error.set(problem.detail);
      return false;
    } finally {
      this.busy.set(false);
    }
  }
}
