import { httpResource } from '@angular/common/http';
import { computed, effect, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { EnergyImportsApi } from '../data-access/energy-imports-api';
import { SessionSource } from '../domain/consumption';
import { ImportBatch, ImportBatchPage, isInProgress } from '../domain/energy-import';

const DEFAULT_PAGE_SIZE = 20;
const POLL_INTERVAL_MS = 3000;

/** Historial de importaciones y carga de nuevos lotes (archivo o pegado). Vive con la pantalla. */
@Service({ autoProvided: false })
export class EnergyImportsFacade {
  private readonly api = inject(EnergyImportsApi);

  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  private readonly result = httpResource<ImportBatchPage>(() =>
    this.api.historyRequest(this.pageIndex(), this.pageSize()),
  );

  readonly batches = computed(() => (this.result.hasValue() ? this.result.value().content : []));
  readonly totalElements = computed(() =>
    this.result.hasValue() ? this.result.value().totalElements : 0,
  );
  readonly loading = this.result.isLoading;
  readonly failed = computed(() => this.result.error() !== undefined);

  constructor() {
    // Sondeo mientras algun lote de la pagina interpreta o confirma; cada respuesta vuelve a armarlo.
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
    this.result.reload();
  }

  /** El lote queda interpretandose en segundo plano; su pantalla muestra el avance. */
  upload(source: SessionSource, file: File): Promise<ImportBatch> {
    return firstValueFrom(this.api.upload(source, file));
  }

  paste(source: SessionSource, text: string): Promise<ImportBatch> {
    return firstValueFrom(this.api.paste(source, text));
  }
}
