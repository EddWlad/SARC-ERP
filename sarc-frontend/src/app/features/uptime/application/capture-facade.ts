import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { GridRowResponse, MeasurementResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { UptimeApi } from '../data-access/uptime-api';
import { dayEndInstant, dayStartInstant } from '../domain/uptime-time';
import { OBSERVATION_LABELS, PeriodType } from '../domain/uptime-labels';

export interface CapturePeriod {
  readonly referenceWeek?: string;
  readonly periodType: PeriodType;
  readonly periodStart: string;
  readonly periodEnd: string;
}

export interface MeasurementDraft {
  readonly periodStart: string;
  readonly periodEnd: string;
  readonly available: string;
  readonly occupied: string;
  readonly fault: string;
  readonly offline: string;
  readonly reportedPercent: string;
}

export interface MeasurementConflict {
  readonly id: string;
  readonly version: number;
}

const EMPTY_DRAFT: MeasurementDraft = {
  periodStart: '',
  periodEnd: '',
  available: '',
  occupied: '',
  fault: '',
  offline: '',
  reportedPercent: '',
};

/** Estado de la grilla y secuencia de captura. El backend decide; aqui solo se avisa. */
@Injectable()
export class CaptureFacade {
  private readonly api = inject(UptimeApi);

  readonly period = signal<CapturePeriod | null>(null);
  readonly showInactive = signal(false);
  readonly equipmentId = signal('');
  readonly rows = signal<GridRowResponse[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly draft = signal<MeasurementDraft>(EMPTY_DRAFT);
  /** Medicion ya registrada con otros valores: lo digitado solo puede entrar como correccion. */
  readonly conflict = signal<MeasurementConflict | null>(null);
  /** Texto del que salio el borrador; se guarda como evidencia del origen BLOCK. */
  private readonly blockText = signal<string | null>(null);
  private readonly currentId = signal<string | null>(null);
  readonly current = computed(
    () => this.rows().find((row) => row.connectorId === this.currentId()) ?? null,
  );

  setPeriod(period: CapturePeriod): void {
    this.period.set(period);
  }

  async load(): Promise<void> {
    const period = this.period();
    if (!period) return;
    this.loading.set(true);
    this.error.set('');
    try {
      const equipmentId = this.equipmentId();
      this.rows.set(
        await firstValueFrom(
          this.api.grid({
            ...(period.referenceWeek
              ? { referenceWeek: period.referenceWeek }
              : { periodStart: period.periodStart, periodEnd: period.periodEnd }),
            showInactive: this.showInactive(),
            ...(equipmentId ? { equipmentId } : {}),
          }),
        ),
      );
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  /** Cada conector empieza en blanco: dos conectores nunca comparten valores. */
  select(connectorId: string | null): void {
    this.currentId.set(connectorId);
    this.draft.set(EMPTY_DRAFT);
    this.blockText.set(null);
    this.conflict.set(null);
    this.error.set('');
  }

  updateDraft(patch: Partial<MeasurementDraft>): void {
    this.draft.update((draft) => ({ ...draft, ...patch }));
  }

  /** Rellena el borrador para revisarlo. Si el bloque no se entiende, lo digitado sigue intacto. */
  async parseBlock(text: string): Promise<boolean> {
    this.busy.set(true);
    this.error.set('');
    try {
      const block = await firstValueFrom(this.api.parseBlock(text));
      this.draft.set({
        ...this.draft(),
        available: String(block.available),
        occupied: String(block.occupied),
        fault: String(block.fault),
        offline: String(block.offline),
        reportedPercent: block.reportedPercent ?? '',
      });
      this.blockText.set(text);
      return true;
    } catch (error) {
      const problem = toProblem(error);
      const missing = problem.properties['missing'] as string[] | undefined;
      this.error.set(
        missing?.length ? `${problem.detail} Faltan: ${missing.join(', ')}.` : problem.detail,
      );
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  /** Devuelve si se guardo. Con error conserva lo digitado y el conector. */
  async save(advance: boolean): Promise<boolean> {
    const period = this.period();
    const current = this.current();
    if (!period || !current) return false;
    const draft = this.draft();
    if (period.referenceWeek && (!draft.periodStart || !draft.periodEnd)) {
      this.error.set('Indique las fechas Desde y Hasta de este conector.');
      return false;
    }
    const sourceText = this.blockText();
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      const saved = await firstValueFrom(
        this.api.register({
          connectorId: current.connectorId,
          source: 'ENERLINK',
          periodStart: draft.periodStart ? dayStartInstant(draft.periodStart) : period.periodStart,
          periodEnd: draft.periodEnd ? dayEndInstant(draft.periodEnd) : period.periodEnd,
          ...(period.referenceWeek ? { referenceWeek: period.referenceWeek } : {}),
          periodType: period.periodType,
          exactConfirmed: false,
          availableMinutes: Number(draft.available),
          occupiedMinutes: Number(draft.occupied),
          faultMinutes: Number(draft.fault),
          offlineMinutes: Number(draft.offline),
          reportedPercent: draft.reportedPercent ? Number(draft.reportedPercent) : undefined,
          origin: sourceText === null ? 'MANUAL' : 'BLOCK',
          ...(sourceText === null ? {} : { sourceText }),
        }),
      );
      this.markSaved(current.connectorId, saved);
      this.notice.set(observationNotice(saved));
      if (advance) this.select(this.nextAfter(current.connectorId));
      return true;
    } catch (error) {
      const problem = toProblem(error);
      this.error.set(problem.detail);
      if (problem.code === 'UPTIME_MEASUREMENT_DIFFERS') {
        this.conflict.set({
          id: problem.properties['id'] as string,
          version: problem.properties['version'] as number,
        });
        await this.load();
        this.error.set(problem.detail);
      }
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  /** Lleva lo digitado como revision nueva de la medicion en conflicto. */
  async saveCorrection(justification: string): Promise<boolean> {
    const period = this.period();
    const conflict = this.conflict();
    const current = this.current();
    if (!period || !conflict || !current) return false;
    const draft = this.draft();
    this.busy.set(true);
    this.error.set('');
    try {
      const saved = await firstValueFrom(
        this.api.correct(conflict.id, {
          version: conflict.version,
          periodType: period.periodType,
          exactConfirmed: false,
          availableMinutes: Number(draft.available),
          occupiedMinutes: Number(draft.occupied),
          faultMinutes: Number(draft.fault),
          offlineMinutes: Number(draft.offline),
          reportedPercent: draft.reportedPercent ? Number(draft.reportedPercent) : undefined,
          justification,
        }),
      );
      this.markSaved(current.connectorId, saved);
      this.conflict.set(null);
      this.notice.set(observationNotice(saved));
      return true;
    } catch (error) {
      const problem = toProblem(error);
      this.error.set(problem.detail);
      if (problem.code === 'CONCURRENT_MODIFICATION') {
        const { id, version } = await firstValueFrom(this.api.detail(conflict.id));
        this.conflict.set({ id, version });
      }
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private markSaved(connectorId: string, saved: MeasurementResponse): void {
    const situation = saved.observations.length > 0 ? 'OBSERVED' : 'PENDING';
    this.rows.update((rows) =>
      rows.map((row) =>
        row.connectorId === connectorId
          ? { ...row, situation, measurementId: saved.id, version: saved.version }
          : row,
      ),
    );
  }

  private nextAfter(connectorId: string): string | null {
    const rows = this.rows();
    const index = rows.findIndex((row) => row.connectorId === connectorId);
    return (
      rows.slice(index + 1).find((row) => row.situation === 'NONE' && row.connectorActive)
        ?.connectorId ?? null
    );
  }
}

function observationNotice(saved: MeasurementResponse): string {
  return saved.observations.map((code) => OBSERVATION_LABELS[code]).join('. ');
}
