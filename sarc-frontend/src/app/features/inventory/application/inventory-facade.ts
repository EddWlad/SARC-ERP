import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import { refreshWhileVisible } from '../../../core/http/auto-refresh';
import { FileSaver } from '../../../core/files/file-saver';
import { EquipmentApi } from '../data-access/equipment-api';
import { InventoryApi } from '../data-access/inventory-api';
import {
  CreateEquipmentInput,
  Equipment,
  EquipmentFilters,
  EquipmentPage,
  NO_FILTERS,
} from '../domain/equipment';
import { ReportFormat } from '../../../shared/reports/report-format';
import { InventorySummary } from '../domain/inventory-summary';

const SEARCH_DEBOUNCE_MS = 300;
const DEFAULT_PAGE_SIZE = 20;

/**
 * Estado de la pantalla de inventario: filtros, listado paginado, indicadores y reportes.
 * Indicadores y reportes usan los mismos filtros que el listado. Vive con la pantalla.
 */
@Service({ autoProvided: false })
export class InventoryFacade {
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly fileSaver = inject(FileSaver);

  readonly filters = signal<EquipmentFilters>(NO_FILTERS);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  readonly downloading = signal<ReportFormat | undefined>(undefined);

  /** El texto libre espera a que el usuario deje de escribir antes de consultar. */
  private readonly searchText = toSignal(
    toObservable(computed(() => this.filters().text)).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );
  private readonly appliedFilters = computed(() => ({
    ...this.filters(),
    text: this.searchText(),
  }));

  private readonly result = httpResource<EquipmentPage>(() =>
    this.equipmentApi.listRequest({
      ...this.appliedFilters(),
      page: this.pageIndex(),
      size: this.pageSize(),
    }),
  );
  private readonly summaryResult = httpResource<InventorySummary>(() =>
    this.inventoryApi.summaryRequest(this.appliedFilters()),
  );

  readonly equipment = computed(() => (this.result.hasValue() ? this.result.value().content : []));
  readonly totalElements = computed(() =>
    this.result.hasValue() ? this.result.value().totalElements : 0,
  );
  readonly loading = this.result.isLoading;
  readonly failed = computed(() => this.result.error() !== undefined);
  readonly summary = computed(() =>
    this.summaryResult.hasValue() ? this.summaryResult.value() : undefined,
  );
  readonly hasFilters = computed(() =>
    Object.values(this.filters()).some((value) => value.trim() !== ''),
  );

  constructor() {
    // Los datos los cambian tambien otros usuarios: se actualizan solos mientras se miran.
    refreshWhileVisible(() => this.reload());
  }

  updateFilters(changes: Partial<EquipmentFilters>): void {
    this.filters.update((current) => ({ ...current, ...changes }));
    this.pageIndex.set(0);
  }

  clearFilters(): void {
    this.updateFilters(NO_FILTERS);
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.result.reload();
    this.summaryResult.reload();
  }

  async create(input: CreateEquipmentInput): Promise<Equipment> {
    const created = await firstValueFrom(this.equipmentApi.create(input));
    this.reload();
    return created;
  }

  /** Descarga el reporte con los filtros aplicados. Si falla, rechaza con el `Problem` del backend. */
  async downloadReport(format: ReportFormat): Promise<void> {
    this.downloading.set(format);
    try {
      await this.fileSaver.download(
        this.inventoryApi.downloadReport(format, this.appliedFilters()),
        `inventario.${format.toLowerCase()}`,
      );
    } finally {
      this.downloading.set(undefined);
    }
  }
}
