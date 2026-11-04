import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FileSaver } from '../../../core/files/file-saver';
import { DashboardFacade } from './dashboard-facade';

const BASE = '/api/v1/uptime';
const page = (content: unknown[] = []) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
});

const summary = (over: object = {}) => ({
  resolvedPeriod: {
    kind: 'PROVISIONAL',
    periodType: 'ROLLING_7D',
    from: '2026-10-24T19:00:00Z',
    to: '2026-10-31T19:00:00Z',
    timeZone: 'America/Guayaquil',
  },
  provisional: { uptimePercent: '97.29' },
  coverage: { included: 1, denominator: 2, byState: {}, pendingLink: 0 },
  trend: [],
  notices: ['NO_OFFICIAL_WEEK'],
  cutOff: '2026-11-01T17:00:00Z',
  ...over,
});

describe('DashboardFacade', () => {
  let facade: DashboardFacade;
  let http: HttpTestingController;
  const files = {
    download: vi.fn((response: { subscribe: (next: object) => void }) => {
      response.subscribe({});
      return Promise.resolve();
    }),
  };

  it('applies a filter at once, drops empty values and clears them all', () => {
    facade.updateFilters({ network: 'JAC', brand: '' });
    expect(facade.filters()).toEqual({ network: 'JAC' });
    expect(facade.hasFilters()).toBe(true);
    facade.clearFilters();
    expect(facade.filters()).toEqual({});
    expect(facade.hasFilters()).toBe(false);
    http.match(() => true);
  });

  it('ignores a slower answer for filters that were already replaced', async () => {
    const summaryFor = (network: string) =>
      http.expectOne((r) => r.url === `${BASE}/summary` && r.params.get('network') === network);
    facade.updateFilters({ network: 'JAC' });
    const stale = summaryFor('JAC');
    facade.updateFilters({ network: 'KIA' });
    summaryFor('KIA').flush(summary({ cutOff: '2026-11-02T17:00:00Z' }));
    stale.flush(summary({ cutOff: '2026-11-01T17:00:00Z' }));
    await vi.waitFor(() => {
      http.match(() => true).forEach((request) => request.flush(page()));
      expect(facade.summary()?.cutOff).toBe('2026-11-02T17:00:00Z');
    });
  });

  it('downloads the report with the applied filters', async () => {
    facade.filters.set({ periodKind: 'OFFICIAL', network: 'JAC' });

    await facade.downloadReport('PDF');

    const request = http.expectOne((item) => item.url === `${BASE}/report`);
    expect(request.request.params.get('format')).toBe('PDF');
    expect(request.request.params.get('periodKind')).toBe('OFFICIAL');
    expect(request.request.params.get('network')).toBe('JAC');
    expect((files.download.mock.calls.at(-1) as unknown[])[1]).toBe('uptime.pdf');
    expect(facade.downloading()).toBeUndefined();
  });

  async function answer(body: object) {
    const loading = facade.load();
    http.expectOne((request) => request.url === `${BASE}/summary`).flush(body);
    await vi.waitFor(() => {
      http.expectOne((request) => request.url === `${BASE}/results/connectors`).flush(page());
      http.expectOne((request) => request.url === `${BASE}/results/equipment`).flush(page());
      http.expectOne((request) => request.url === `${BASE}/results/exclusions`).flush(page());
    });
    await loading;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardFacade,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileSaver, useValue: files },
      ],
    });
    facade = TestBed.inject(DashboardFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('rotula la cifra con el tipo y el rango real', async () => {
    await answer(summary());

    expect(facade.headline()?.label).toBe('PROVISIONAL');
    expect(facade.headline()?.uptimePercent).toBe('97.29');
    expect(facade.rangeLabel()).toContain('24/10/2026');
    expect(facade.rangeLabel()).toContain('America/Guayaquil');
  });

  it('traduce los avisos', async () => {
    await answer(summary());

    expect(facade.notices()[0]).toContain('cifra oficial');
  });

  it('sin tendencia explica por que', async () => {
    await answer(summary());

    expect(facade.trendAvailable()).toBe(false);
  });

  it('oculta el bloque operativo si no llega', async () => {
    await answer(summary());
    expect(facade.summary()?.operations).toBeUndefined();

    await answer(
      summary({ operations: { observed: 1, readyToValidate: 0, correctionsPending: 0 } }),
    );
    expect(facade.summary()?.operations?.observed).toBe(1);
  });

  it('el filtro de periodo viaja al backend', async () => {
    facade.filters.update((filters) => ({ ...filters, network: 'JAC' }));
    const loading = facade.load();
    const request = http.expectOne((call) => call.url === `${BASE}/summary`);
    expect(request.request.params.get('network')).toBe('JAC');
    expect(request.request.params.has('from')).toBe(false);
    request.flush({ detail: 'x' }, { status: 500, statusText: 'Error' });
    await loading;
  });

  it('un fallo de refresco conserva los datos y avisa', async () => {
    await answer(summary());

    const refreshing = facade.refresh();
    http
      .expectOne((request) => request.url === `${BASE}/summary`)
      .flush({ detail: 'x' }, { status: 500, statusText: 'Error' });
    await refreshing;

    expect(facade.refreshFailed()).toBe(true);
    expect(facade.headline()?.uptimePercent).toBe('97.29');
  });

  it('un refresco correcto limpia el aviso', async () => {
    await answer(summary());
    facade.refreshFailed.set(true);

    await answer(summary());

    expect(facade.refreshFailed()).toBe(false);
  });
});
