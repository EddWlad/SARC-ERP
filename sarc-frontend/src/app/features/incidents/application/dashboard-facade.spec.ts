import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { TicketPageResponse } from '../../../core/api/generated';
import { FileSaver } from '../../../core/files/file-saver';
import { summaryResponse } from '../testing/summary-fixture';
import { DashboardFacade } from './dashboard-facade';

const SUMMARY_URL = '/api/v1/incidents/summary';
const TICKETS_URL = '/api/v1/incidents/tickets';

function page(total: number): TicketPageResponse {
  return { items: [], total, page: 0, size: 1, cutOff: '2026-11-22T20:30:00Z' };
}

describe('DashboardFacade', () => {
  let http: HttpTestingController;
  let facade: DashboardFacade;
  const saver = { download: vi.fn().mockResolvedValue(undefined) };

  beforeEach(() => {
    saver.download.mockClear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileSaver, useValue: saver },
        DashboardFacade,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(DashboardFacade);
  });

  afterEach(() => http.verify());

  /** El efecto del constructor pide el resumen y los tres conteos de abiertos por condicion. */
  async function settle(summary = summaryResponse(), counts = [3, 2, 1]) {
    TestBed.tick();
    const summaryRequest = http.expectOne((r) => r.url === SUMMARY_URL);
    const conditions = http.match((r) => r.url === TICKETS_URL);
    summaryRequest.flush(summary);
    for (const request of conditions) {
      const kind = request.request.params.get('condition');
      const index = ['PENDING_VALIDATION', 'IN_ATTENTION', 'RECOVERED_OBSERVING'].indexOf(
        kind ?? '',
      );
      request.flush(page(counts[index]));
    }
    await vi.waitFor(() => expect(facade.loading()).toBe(false));
    return { summaryRequest, conditions };
  }

  it('abre en «Este mes» con los dias inclusivos y pide los tres conteos de abiertos', async () => {
    const { summaryRequest, conditions } = await settle();

    const params = summaryRequest.request.params;
    expect(params.get('from')).toMatch(/^\d{4}-\d{2}-01$/);
    expect(params.get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(params.has('network')).toBe(false);
    expect(conditions).toHaveLength(3);
    expect(facade.summary()?.totals.episodes).toBe(5);
    expect(facade.conditions()).toEqual({
      pendingValidation: 3,
      inAttention: 2,
      recoveredObserving: 1,
    });
    expect(facade.refresh.lastUpdatedAt()).not.toBeNull();
  });

  it('un filtro vuelve a consultar con ese filtro, tambien en los conteos de abiertos', async () => {
    await settle();

    facade.filter('priority', 'CRITICAL');
    TestBed.tick();
    const summaryRequest = http.expectOne((r) => r.url === SUMMARY_URL);
    const conditions = http.match((r) => r.url === TICKETS_URL);
    expect(summaryRequest.request.params.get('priority')).toBe('CRITICAL');
    expect(conditions.every((r) => r.request.params.get('priority') === 'CRITICAL')).toBe(true);
    summaryRequest.flush(summaryResponse());
    conditions.forEach((request) => request.flush(page(0)));
    await vi.waitFor(() => expect(facade.loading()).toBe(false));

    expect(facade.hasFilters()).toBe(true);
    facade.clearFilters();
    TestBed.tick();
    http
      .match(() => true)
      .forEach((request) =>
        request.flush(request.request.url === SUMMARY_URL ? summaryResponse() : page(0)),
      );
    await vi.waitFor(() => expect(facade.loading()).toBe(false));
    expect(facade.hasFilters()).toBe(false);
  });

  it('descargar manda el periodo y los filtros que se ven', async () => {
    await settle();
    saver.download.mockImplementationOnce(async (source) => {
      source.subscribe();
      const call = http.expectOne((r) => r.url === '/api/v1/incidents/reports/management');
      expect(call.request.params.get('format')).toBe('XLSX');
      expect(call.request.params.get('from')).toMatch(/^\d{4}-\d{2}-01$/);
      call.flush(new Blob(['x']));
    });

    await facade.downloadReport('XLSX');

    expect(saver.download.mock.calls[0][1]).toBe('gestion-incidencias.xlsx');
    expect(facade.downloading()).toBeUndefined();
  });

  it('si la primera carga falla lo dice; si falla una actualizacion conserva las cifras', async () => {
    TestBed.tick();
    http
      .match(() => true)
      .forEach((request) =>
        request.flush(
          { code: 'X', detail: 'No se pudo cargar' },
          { status: 500, statusText: 'Error' },
        ),
      );
    await vi.waitFor(() => expect(facade.failed()).toBe(true));
    expect(facade.errorMessage()).toBe('No se pudo cargar');

    const retry = facade.load();
    http
      .match(() => true)
      .forEach((request) =>
        request.flush(request.request.url === SUMMARY_URL ? summaryResponse() : page(1)),
      );
    await retry;
    expect(facade.failed()).toBe(false);

    const refresh = facade.load();
    http
      .match(() => true)
      .forEach((request) =>
        request.flush({ code: 'X', detail: 'Caído' }, { status: 500, statusText: 'Error' }),
      );
    await refresh;
    expect(facade.refreshFailed()).toBe(true);
    expect(facade.summary()?.totals.episodes).toBe(5);
  });
});
