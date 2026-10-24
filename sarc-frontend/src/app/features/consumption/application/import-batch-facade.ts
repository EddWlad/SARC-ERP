import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { computed, effect, inject, Service, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom, map, Observable } from 'rxjs';

import { EnergyImportsApi, RowDecision } from '../data-access/energy-imports-api';
import {
  ImportBatch,
  ImportRow,
  ImportRowPage,
  isInProgress,
  RowClassification,
} from '../domain/energy-import';

/** Cada cuanto se consulta un lote que trabaja en segundo plano. */
const POLL_INTERVAL_MS = 1500;
const DEFAULT_PAGE_SIZE = 50;

/**
 * Vista previa y seguimiento de un lote (ruta `/consumo/importaciones/:id`). Mientras el backend
 * interpreta o confirma, el lote se vuelve a consultar; al cambiar de estado se recargan las filas.
 */
@Service({ autoProvided: false })
export class ImportBatchFacade {
  private readonly api = inject(EnergyImportsApi);

  readonly id = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('id'))), {
    initialValue: null,
  });
  readonly classification = signal<RowClassification | ''>('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly busy = signal(false);

  private readonly batchResult = httpResource<ImportBatch>(() => {
    const id = this.id();
    return id === null ? undefined : this.api.batchUrl(id);
  });

  readonly batch = computed(() =>
    this.batchResult.hasValue() ? this.batchResult.value() : undefined,
  );
  private readonly status = computed(() => this.batch()?.status);
  readonly inProgress = computed(() => {
    const status = this.status();
    return status !== undefined && isInProgress(status);
  });
  readonly notFound = computed(() => {
    const error = this.batchResult.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });
  readonly failed = computed(() => this.batchResult.error() !== undefined && !this.notFound());
  /** Las decisiones solo se toman mientras la vista previa esta abierta. */
  readonly reviewable = computed(() => this.status() === 'READY');

  /** Sin filas hasta que termina de interpretarse el archivo. */
  private readonly rowsResult = httpResource<ImportRowPage>(() => {
    const id = this.id();
    const status = this.status();
    if (id === null || status === undefined || status === 'PARSING') {
      return undefined;
    }
    return this.api.rowsRequest(id, this.classification(), this.pageIndex(), this.pageSize());
  });

  readonly rows = computed(() =>
    this.rowsResult.hasValue() ? this.rowsResult.value().content : [],
  );
  readonly rowsTotal = computed(() =>
    this.rowsResult.hasValue() ? this.rowsResult.value().totalElements : 0,
  );
  readonly rowsLoading = this.rowsResult.isLoading;

  constructor() {
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
      const status = this.status();
      if (previous !== undefined && status !== previous) {
        untracked(() => this.rowsResult.reload());
      }
      previous = status;
    });
  }

  filterBy(classification: RowClassification | ''): void {
    this.classification.set(classification);
    this.pageIndex.set(0);
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.batchResult.reload();
    this.rowsResult.reload();
  }

  /** Aplica la decision y reemplaza las filas afectadas en la pagina visible. */
  async decide(rows: readonly ImportRow[], decision: RowDecision): Promise<void> {
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

  confirm(): Promise<ImportBatch> {
    return this.change((batch) => this.api.confirm(batch));
  }

  retry(): Promise<ImportBatch> {
    return this.change((batch) => this.api.retry(batch));
  }

  discard(): Promise<ImportBatch> {
    return this.change((batch) => this.api.discard(batch));
  }

  private async change(
    request: (batch: ImportBatch) => Observable<ImportBatch>,
  ): Promise<ImportBatch> {
    this.busy.set(true);
    try {
      const updated = await firstValueFrom(request(this.requireBatch()));
      this.batchResult.set(updated);
      return updated;
    } finally {
      this.busy.set(false);
    }
  }

  private requireBatch(): ImportBatch {
    const batch = this.batch();
    if (batch === undefined) {
      throw new Error('El lote aun no esta cargado');
    }
    return batch;
  }
}
