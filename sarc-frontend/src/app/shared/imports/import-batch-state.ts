import {
  HttpErrorResponse,
  HttpResourceRef,
  HttpResourceRequest,
  httpResource,
} from '@angular/common/http';
import { computed, effect, inject, Signal, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom, map, Observable } from 'rxjs';

import { ImportBatchLike, isInProgress } from './import-batch';
import { retryTransientFailures, TransientRetry } from './transient-retry';

/** Cada cuanto se consulta un lote que trabaja en segundo plano. */
const POLL_INTERVAL_MS = 1500;
const DEFAULT_PAGE_SIZE = 50;

/** Pagina de filas de una vista previa. */
export interface ImportRowPage<R> {
  readonly content: R[];
  readonly totalElements: number;
}

/** Operaciones HTTP de un lote que aporta cada modulo (consumos, equipos). */
export interface ImportBatchApi<B, R, C extends string, D> {
  batchUrl(id: string): string;
  rowsRequest(id: string, classification: C | '', page: number, size: number): HttpResourceRequest;
  decide(id: string, rowIds: readonly string[], decision: D): Observable<R[]>;
  confirm(batch: B): Observable<B>;
  retry(batch: B): Observable<B>;
  discard(batch: B): Observable<B>;
}

/**
 * Vista previa y seguimiento de un lote (ruta `.../importaciones/:id`), comun a todas las
 * importaciones. Mientras el backend interpreta o confirma, el lote se vuelve a consultar; al
 * cambiar de estado se recargan las filas. Cada modulo lo extiende con su API.
 */
export abstract class ImportBatchState<
  B extends ImportBatchLike,
  R extends { readonly id: string },
  C extends string,
  D,
> {
  readonly id = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('id'))), {
    initialValue: null,
  });
  readonly classification = signal<C | ''>('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly busy = signal(false);

  readonly batch: Signal<B | undefined>;
  readonly inProgress: Signal<boolean>;
  readonly notFound: Signal<boolean>;
  readonly failed: Signal<boolean>;
  /** Las decisiones solo se toman mientras la vista previa esta abierta. */
  readonly reviewable: Signal<boolean>;
  /** Fallo transitorio al consultar el lote; se reintenta solo. */
  readonly reconnecting: Signal<boolean>;
  readonly rows: Signal<R[]>;
  readonly rowsTotal: Signal<number>;
  readonly rowsLoading: Signal<boolean>;
  readonly rowsFailed: Signal<boolean>;
  /** Solo se confirma lo que se pudo revisar: con las filas cargadas y sin error. */
  readonly canConfirm: Signal<boolean>;

  private readonly batchResult: HttpResourceRef<B | undefined>;
  private readonly rowsResult: HttpResourceRef<ImportRowPage<R> | undefined>;
  private readonly batchRetry: TransientRetry;

  protected constructor(private readonly api: ImportBatchApi<B, R, C, D>) {
    this.batchResult = httpResource<B>(() => {
      const id = this.id();
      return id === null ? undefined : api.batchUrl(id);
    });
    this.batch = computed(() =>
      this.batchResult.hasValue() ? this.batchResult.value() : undefined,
    );
    const status = computed(() => this.batch()?.status);
    this.inProgress = computed(() => {
      const current = status();
      return current !== undefined && isInProgress(current);
    });
    this.notFound = computed(() => {
      const error = this.batchResult.error();
      return error instanceof HttpErrorResponse && error.status === 404;
    });
    this.failed = computed(() => this.batchResult.error() !== undefined && !this.notFound());
    this.reviewable = computed(() => status() === 'READY');

    // Sin filas hasta que termina de interpretarse el archivo.
    this.rowsResult = httpResource<ImportRowPage<R>>(() => {
      const id = this.id();
      const current = status();
      if (id === null || current === undefined || current === 'PARSING') {
        return undefined;
      }
      return api.rowsRequest(id, this.classification(), this.pageIndex(), this.pageSize());
    });
    this.rows = computed(() => (this.rowsResult.hasValue() ? this.rowsResult.value().content : []));
    this.rowsTotal = computed(() =>
      this.rowsResult.hasValue() ? this.rowsResult.value().totalElements : 0,
    );
    this.rowsLoading = this.rowsResult.isLoading;
    this.rowsFailed = computed(() => this.rowsResult.error() !== undefined);
    this.canConfirm = computed(
      () => this.reviewable() && this.rowsResult.hasValue() && !this.rowsResult.isLoading(),
    );
    this.batchRetry = retryTransientFailures(this.batchResult, POLL_INTERVAL_MS);
    this.reconnecting = this.batchRetry.retrying;

    // Sondeo: efecto legitimo (temporizador externo), no propaga estado. Depende del lote, no de
    // `inProgress`: cada respuesta es un objeto nuevo y vuelve a armar el temporizador, mientras
    // que `inProgress` seguiria en true y el sondeo se detendria tras la primera consulta.
    effect((onCleanup) => {
      const batch = this.batch();
      if (batch === undefined || !isInProgress(batch.status)) {
        return;
      }
      const timer = setTimeout(() => this.batchResult.reload(), POLL_INTERVAL_MS);
      onCleanup(() => clearTimeout(timer));
    });
    // Al confirmar cambian los resultados de las filas aunque la peticion sea la misma.
    let previous: string | undefined;
    effect(() => {
      const current = status();
      if (previous !== undefined && current !== previous) {
        untracked(() => this.rowsResult.reload());
      }
      previous = current;
    });
  }

  filterBy(classification: C | ''): void {
    this.classification.set(classification);
    this.pageIndex.set(0);
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.batchRetry.reset();
    this.batchResult.reload();
    this.rowsResult.reload();
  }

  reloadRows(): void {
    this.rowsResult.reload();
  }

  /** Aplica la decision y reemplaza las filas afectadas en la pagina visible. */
  async decide(rows: readonly R[], decision: D): Promise<void> {
    const batch = this.requireBatch();
    const updated = await firstValueFrom(
      this.api.decide(
        batch.id,
        rows.map((row) => row.id),
        decision,
      ),
    );
    const byId = new Map(updated.map((row) => [row.id, row]));
    this.rowsResult.update((page) =>
      page === undefined
        ? page
        : { ...page, content: page.content.map((row) => byId.get(row.id) ?? row) },
    );
  }

  confirm(): Promise<B> {
    return this.change((batch) => this.api.confirm(batch));
  }

  retry(): Promise<B> {
    return this.change((batch) => this.api.retry(batch));
  }

  discard(): Promise<B> {
    return this.change((batch) => this.api.discard(batch));
  }

  private async change(request: (batch: B) => Observable<B>): Promise<B> {
    this.busy.set(true);
    try {
      const updated = await firstValueFrom(request(this.requireBatch()));
      this.batchResult.set(updated);
      return updated;
    } finally {
      this.busy.set(false);
    }
  }

  private requireBatch(): B {
    const batch = this.batch();
    if (batch === undefined) {
      throw new Error('El lote aun no esta cargado');
    }
    return batch;
  }
}
