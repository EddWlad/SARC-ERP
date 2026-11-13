import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CaptureResultResponse, KiaPointResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IntermittencyApi } from '../data-access/intermittency-api';
import {
  addDays,
  DaySlice,
  minutesBetween,
  operatingInstant,
  splitByDay,
} from '../domain/intermittency-time';

/** Una ventana que se esta digitando. `endDate` fija un fin con fecha completa, por ejemplo una caida de varios dias. */
export interface WindowDraft {
  readonly key: number;
  readonly start: string;
  readonly end: string;
  readonly open: boolean;
  readonly endsNextDay: boolean;
  readonly endDate: string;
  readonly comment: string;
}

/** Una ventana digitada con lo que se calcula al vuelo. */
export interface WindowRowView extends WindowDraft {
  /** Fecha del fin: la indicada, el dia siguiente si termina al dia siguiente, o la fecha de inicio. */
  readonly endDay: string;
  readonly minutes: number | null;
  /** Problema que impide guardar la fila; vacio si esta bien. */
  readonly issue: string;
  /** Aviso que no impide guardar. */
  readonly warning: string;
  readonly blank: boolean;
}

export interface DayTotalView {
  readonly day: string;
  readonly minutes: number;
}

const LONG_WINDOW_MINUTES = 240;

function withoutKey(errors: Readonly<Record<number, string>>, key: number): Record<number, string> {
  const copy = { ...errors };
  delete copy[key];
  return copy;
}

/** Estado de la captura manual: un punto, un dia y todas las ventanas que se necesiten. El backend decide. */
@Injectable()
export class CaptureFacade {
  private readonly api = inject(IntermittencyApi);
  private nextKey = 1;

  readonly point = signal<KiaPointResponse | null>(null);
  readonly date = signal('');
  readonly rows = signal<WindowDraft[]>([this.blankRow()]);
  readonly busy = signal(false);
  readonly error = signal('');
  /** Errores del servidor por fila, por la clave de la fila. */
  readonly rowErrors = signal<Readonly<Record<number, string>>>({});
  /** Ultima captura guardada, para confirmarla en pantalla. */
  readonly saved = signal<CaptureResultResponse | null>(null);
  /** Sube con cada captura guardada: el panel de ventanas abiertas se recarga al cambiar. */
  readonly savedCount = signal(0);

  readonly rowViews = computed<WindowRowView[]>(() => this.rows().map((row) => this.view(row)));

  /** Total de cada dia local, solo con las filas cerradas y validas: una que cruza la medianoche aporta a ambos. */
  readonly dayTotals = computed<DayTotalView[]>(() => {
    const totals = new Map<string, number>();
    for (const row of this.rowViews()) {
      if (row.blank || row.open || row.issue) continue;
      for (const slice of this.slices(row)) {
        totals.set(slice.day, (totals.get(slice.day) ?? 0) + slice.minutes);
      }
    }
    return [...totals.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([day, minutes]) => ({ day, minutes }));
  });

  readonly filledRows = computed(() => this.rowViews().filter((row) => !row.blank));

  readonly canSave = computed(
    () =>
      this.point() !== null &&
      this.date() !== '' &&
      this.filledRows().length > 0 &&
      this.filledRows().every((row) => !row.issue),
  );

  selectPoint(point: KiaPointResponse | null): void {
    this.point.set(point);
    this.error.set('');
  }

  setDate(date: string): void {
    this.date.set(date);
    this.error.set('');
  }

  addRow(): void {
    this.rows.update((rows) => [...rows, this.blankRow()]);
  }

  /** Quita una fila; siempre queda al menos una para escribir. */
  removeRow(key: number): void {
    this.rows.update((rows) => {
      const left = rows.filter((row) => row.key !== key);
      return left.length > 0 ? left : [this.blankRow()];
    });
    this.rowErrors.update((errors) => withoutKey(errors, key));
  }

  updateRow(key: number, patch: Partial<Omit<WindowDraft, 'key'>>): void {
    this.rows.update((rows) => rows.map((row) => (row.key === key ? { ...row, ...patch } : row)));
    this.rowErrors.update((errors) => withoutKey(errors, key));
    this.error.set('');
  }

  searchPoints(text: string) {
    return this.api.searchPoints(text);
  }

  /**
   * Guarda las ventanas del punto en una sola operacion atomica. Con `another` queda libre el punto y se
   * conserva la fecha para registrar el siguiente (FR-019). Devuelve si se guardo.
   */
  async save(another: boolean): Promise<boolean> {
    const point = this.point();
    if (!point || !this.canSave()) return false;
    const filled = this.filledRows();
    this.busy.set(true);
    this.error.set('');
    this.rowErrors.set({});
    try {
      const result = await firstValueFrom(
        this.api.captureWindows({
          equipmentId: point.id,
          windows: filled.map((row) => ({
            startAt: operatingInstant(this.date(), row.start),
            ...(row.open ? {} : { endAt: operatingInstant(row.endDay, row.end) }),
            ...(row.comment.trim() ? { comment: row.comment.trim() } : {}),
          })),
        }),
      );
      this.saved.set(result);
      this.savedCount.update((count) => count + 1);
      this.rows.set([this.blankRow()]);
      if (another) this.point.set(null);
      return true;
    } catch (error) {
      const problem = toProblem(error);
      this.error.set(problem.detail);
      this.rowErrors.set(this.errorsByRow(problem.properties['errors'], filled));
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private view(row: WindowDraft): WindowRowView {
    const date = this.date();
    const endDay = row.endDate || (row.endsNextDay && date ? addDays(date, 1) : date);
    const blank = !row.start && !row.end && !row.open && !row.comment.trim() && !row.endDate;
    const minutes =
      row.open || !row.start || !row.end || !date
        ? null
        : minutesBetween(date, row.start, endDay, row.end);
    let issue = '';
    let warning = '';
    if (!blank) {
      if (!row.start) issue = 'Indique la hora de inicio.';
      else if (!row.open && !row.end)
        issue = 'Indique la hora de fin o marque la ventana como abierta.';
      else if (minutes !== null && minutes <= 0)
        issue = row.endDate
          ? 'El fin debe ser posterior al inicio.'
          : 'El fin debe ser posterior al inicio. Si termina al día siguiente, márquelo.';
      else if (minutes !== null && minutes >= LONG_WINDOW_MINUTES)
        warning = 'Dura 4 horas o más: quedará por revisar.';
    }
    return { ...row, endDay, minutes, issue, warning, blank };
  }

  private slices(row: WindowRowView): DaySlice[] {
    return splitByDay(this.date(), row.start, row.endDay, row.end);
  }

  /** Los indices del servidor cuentan solo las filas con datos, en orden: se llevan a la fila que corresponde. */
  private errorsByRow(errors: unknown, filled: readonly WindowRowView[]): Record<number, string> {
    const result: Record<number, string> = {};
    if (!Array.isArray(errors)) return result;
    for (const item of errors as { index?: number; message?: string }[]) {
      const row = item.index === undefined ? undefined : filled[item.index];
      if (row && item.message) result[row.key] = item.message;
    }
    return result;
  }

  private blankRow(): WindowDraft {
    return {
      key: this.nextKey++,
      start: '',
      end: '',
      open: false,
      endsNextDay: false,
      endDate: '',
      comment: '',
    };
  }
}
