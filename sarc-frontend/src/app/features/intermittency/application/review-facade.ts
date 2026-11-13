import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  CorrectWindowRequest,
  KiaPointResponse,
  PendingRowResponse,
  ResolvePendingRowRequest,
  WindowResponse,
} from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IntermittencyImportsApi } from '../data-access/intermittency-imports-api';
import { IntermittencyApi } from '../data-access/intermittency-api';
import { IntermittencyImportRow } from '../domain/intermittency-import';
import { addDays } from '../domain/intermittency-time';
import { ImportRowActions } from './import-row-actions';
import { WindowCloser } from './open-windows-facade';

/** Secciones de la revision: lo que un editor tiene que mirar. */
export type ReviewSection = 'REVIEW' | 'OPEN' | 'TEST' | 'PENDING';

const SIZE = 100;

/** Que cambiaria marcar un punto como prueba en un rango, antes de confirmarlo. */
export interface PointTestPreview {
  readonly willMark: number;
  readonly openNotMarked: number;
}

/**
 * Revision de lo que no es oficial todavia: ventanas por revisar, abiertas y de prueba, y filas pendientes de
 * lotes confirmados. Cada cambio lleva justificacion, vuelve a cargar la seccion y conserva el historial
 * (FR-017, FR-021a, FR-028). Tambien sirve de adaptador para cerrar ventanas abiertas y resolver filas pendientes.
 */
@Injectable()
export class ReviewFacade implements WindowCloser {
  private readonly api = inject(IntermittencyApi);
  private readonly imports = inject(IntermittencyImportsApi);

  private ticket = 0;

  readonly section = signal<ReviewSection>('REVIEW');
  readonly windows = signal<WindowResponse[]>([]);
  readonly pending = signal<PendingRowResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly error = signal('');
  /** Cantidad de cada seccion, para los rotulos de las pestanas. */
  readonly counts = signal<Readonly<Record<ReviewSection, number>>>({
    REVIEW: 0,
    OPEN: 0,
    TEST: 0,
    PENDING: 0,
  });
  readonly empty = computed(() => !this.loading() && !this.error() && this.rows() === 0);

  private rows(): number {
    return this.section() === 'PENDING' ? this.pending().length : this.windows().length;
  }

  async select(section: ReviewSection): Promise<void> {
    this.section.set(section);
    await this.load();
  }

  /** Carga la seccion elegida y las cantidades de todas. */
  async load(): Promise<void> {
    const ticket = ++this.ticket;
    const section = this.section();
    this.loading.set(true);
    this.error.set('');
    try {
      const [review, open, test, pending] = await Promise.all([
        firstValueFrom(
          this.api.listWindows({ states: ['REVIEW'], size: section === 'REVIEW' ? SIZE : 1 }),
        ),
        firstValueFrom(
          this.api.listWindows({ states: ['OPEN'], size: section === 'OPEN' ? SIZE : 1 }),
        ),
        firstValueFrom(
          this.api.listWindows({ states: ['TEST'], size: section === 'TEST' ? SIZE : 1 }),
        ),
        firstValueFrom(this.imports.pendingRows(0, section === 'PENDING' ? SIZE : 1)),
      ]);
      // Una respuesta tardia de otra seccion no pisa la actual.
      if (ticket !== this.ticket) return;
      this.counts.set({
        REVIEW: review.totalElements,
        OPEN: open.totalElements,
        TEST: test.totalElements,
        PENDING: pending.totalElements,
      });
      this.windows.set(
        { REVIEW: review, OPEN: open, TEST: test, PENDING: null }[section]?.content ?? [],
      );
      this.pending.set(section === 'PENDING' ? pending.content : []);
      this.total.set(this.counts()[section]);
    } catch (error) {
      if (ticket !== this.ticket) return;
      this.error.set(toProblem(error).detail);
    } finally {
      if (ticket === this.ticket) this.loading.set(false);
    }
  }

  /** Cada cambio falla con el mensaje del servidor (409 incluido) para que el dialogo lo muestre y conserve lo escrito. */
  confirmReview(window: WindowResponse, justification: string): Promise<WindowResponse> {
    return this.change(window, 'confirm-review', justification);
  }

  markTest(window: WindowResponse, justification: string): Promise<WindowResponse> {
    return this.change(window, 'test', justification);
  }

  unmarkTest(window: WindowResponse, justification: string): Promise<WindowResponse> {
    return this.change(window, 'untest', justification);
  }

  annul(window: WindowResponse, justification: string): Promise<WindowResponse> {
    return this.change(window, 'annul', justification);
  }

  /** Corrige el inicio, el fin o el comentario; el servidor guarda la revision anterior. */
  correct(
    window: WindowResponse,
    body: Omit<CorrectWindowRequest, 'version'>,
  ): Promise<WindowResponse> {
    return this.apply(() =>
      firstValueFrom(this.api.correctWindow(window.id, { version: window.version, ...body })),
    );
  }

  /** Registra el fin de una ventana abierta; falla con el mensaje del servidor. */
  async close(window: WindowResponse, endAt: string): Promise<WindowResponse> {
    try {
      const closed = await firstValueFrom(
        this.api.closeWindow(window.id, { version: window.version, endAt }),
      );
      await this.load();
      return closed;
    } catch (error) {
      throw new Error(toProblem(error).detail, { cause: error });
    }
  }

  /** Cuantas ventanas se marcarian y cuantas abiertas quedarian sin marcar, antes de confirmar. */
  async previewPointTest(
    point: KiaPointResponse,
    firstDay: string,
    lastDay: string,
  ): Promise<PointTestPreview> {
    const range = { equipmentIds: [point.id], from: firstDay, to: addDays(lastDay, 1), size: 1 };
    const [marked, open] = await Promise.all([
      firstValueFrom(this.api.listWindows({ ...range, states: ['CONFIRMED', 'REVIEW'] })),
      firstValueFrom(this.api.listWindows({ ...range, states: ['OPEN'] })),
    ]);
    return { willMark: marked.totalElements, openNotMarked: open.totalElements };
  }

  /** Marca o quita la prueba de un punto en un rango; devuelve lo que informo el servidor. */
  async pointTest(
    point: KiaPointResponse,
    mark: boolean,
    firstDay: string,
    lastDay: string,
    justification: string,
  ): Promise<{ affectedWindows: number; openWindowsNotMarked: number }> {
    try {
      const result = await firstValueFrom(
        this.api.pointTest(point.id, mark, {
          from: firstDay,
          to: addDays(lastDay, 1),
          justification,
        }),
      );
      await this.load();
      return result;
    } catch (error) {
      throw new Error(toProblem(error).detail, { cause: error });
    }
  }

  /** Adaptador para resolver filas pendientes con el mismo panel de decisiones de la vista previa. */
  rowActions(pending: PendingRowResponse): ImportRowActions {
    // El lote ya esta confirmado: vincular y decidir viajan juntos en una sola resolucion.
    let equipmentId: string | null = pending.row.equipmentId ?? null;
    return {
      editRow: () =>
        Promise.reject(new Error('La fila ya se confirmó: no se edita, solo se resuelve.')),
      linkRow: (row: IntermittencyImportRow, id: string) => {
        equipmentId = id;
        return Promise.resolve({ row: { ...row, equipmentId: id } });
      },
      decideRow: (row: IntermittencyImportRow, body) =>
        this.resolve(pending.batchId, row, { ...body, ...(equipmentId ? { equipmentId } : {}) }),
    };
  }

  private async resolve(
    batchId: string,
    row: IntermittencyImportRow,
    body: Omit<ResolvePendingRowRequest, 'version'>,
  ) {
    try {
      const changed = await firstValueFrom(this.imports.resolveRow(batchId, row, body));
      await this.load();
      return changed;
    } catch (error) {
      throw new Error(toProblem(error).detail, { cause: error });
    }
  }

  private change(
    window: WindowResponse,
    action: 'confirm-review' | 'test' | 'untest' | 'annul',
    justification: string,
  ): Promise<WindowResponse> {
    return this.apply(() =>
      firstValueFrom(
        this.api.changeWindow(window.id, action, { version: window.version, justification }),
      ),
    );
  }

  /** Ejecuta un cambio y recarga lo que se ve; con error lanza el mensaje del servidor. */
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
