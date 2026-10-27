import { HttpResourceRef, HttpResourceRequest, httpResource } from '@angular/common/http';
import { computed, effect, Signal, signal } from '@angular/core';

import { ImportBatchLike, isInProgress } from './import-batch';
import { retryTransientFailures, TransientRetry } from './transient-retry';

const DEFAULT_PAGE_SIZE = 20;
const POLL_INTERVAL_MS = 3000;

/** Pagina del historial de importaciones. */
export interface ImportHistoryPage<B> {
  readonly content: B[];
  readonly totalElements: number;
}

/** Historial de lotes de una importacion, el mas reciente primero. */
export interface ImportHistoryApi {
  historyRequest(page: number, size: number): HttpResourceRequest;
}

/**
 * Historial de importaciones comun a todos los modulos. Mientras algun lote de la pagina interpreta
 * o confirma, se vuelve a consultar para que su estado no quede desactualizado.
 */
export abstract class ImportHistoryState<
  B extends ImportBatchLike,
  A extends ImportHistoryApi = ImportHistoryApi,
> {
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  readonly batches: Signal<B[]>;
  readonly totalElements: Signal<number>;
  readonly loading: Signal<boolean>;
  readonly failed: Signal<boolean>;
  /** Fallo transitorio; se reintenta solo antes de dejar la accion manual. */
  readonly reconnecting: Signal<boolean>;

  private readonly result: HttpResourceRef<ImportHistoryPage<B> | undefined>;
  private readonly retry: TransientRetry;

  /** API del modulo, para sus propias operaciones (cargar, pegar, plantilla). */
  protected constructor(protected readonly api: A) {
    this.result = httpResource<ImportHistoryPage<B>>(() =>
      api.historyRequest(this.pageIndex(), this.pageSize()),
    );
    this.batches = computed(() => (this.result.hasValue() ? this.result.value().content : []));
    this.totalElements = computed(() =>
      this.result.hasValue() ? this.result.value().totalElements : 0,
    );
    this.loading = this.result.isLoading;
    this.failed = computed(() => this.result.error() !== undefined);
    this.retry = retryTransientFailures(this.result, POLL_INTERVAL_MS);
    this.reconnecting = this.retry.retrying;

    // Sondeo mientras algun lote trabaja; cada respuesta es un objeto nuevo y vuelve a armarlo.
    effect((onCleanup) => {
      if (!this.batches().some((batch) => isInProgress(batch.status))) {
        return;
      }
      const timer = setTimeout(() => this.result.reload(), POLL_INTERVAL_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.retry.reset();
    this.result.reload();
  }
}
