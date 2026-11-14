import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AUTO_REFRESH_MS, HUMAN_IDLE_MS } from '../../../core/http/auto-refresh';
import { ConsumptionDashboardFacade } from './consumption-dashboard-facade';

const SUMMARY_URL = '/api/v1/energy/summary';

function summary(energyKwh: number) {
  return { official: { sessions: 2, energyKwh, equipment: 1, sessionsWithoutEnergy: 0 } };
}

describe('ConsumptionDashboardFacade refresh (Feature 009, US4)', () => {
  let facade: ConsumptionDashboardFacade;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ConsumptionDashboardFacade],
    });
    facade = TestBed.inject(ConsumptionDashboardFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function pending(): TestRequest {
    TestBed.tick();
    return http.expectOne((request) => request.url === SUMMARY_URL);
  }

  async function settle(): Promise<void> {
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
  }

  /** Carga inicial; la vista lee las cifras en cada detección de cambios. */
  async function loaded(energyKwh = 13): Promise<void> {
    pending().flush(summary(energyKwh));
    await settle();
    expect(facade.summary()?.official.energyKwh).toBe(energyKwh);
  }

  it('keeps the same figures through a refresh with equivalent data', async () => {
    await loaded();
    const shown = facade.summary();

    facade.reload();
    expect(facade.summary()).toBe(shown);
    pending().flush(summary(13));
    await settle();

    expect(facade.summary()?.official.energyKwh).toBe(13);
    expect(facade.error()).toBeUndefined();
    expect(facade.refreshError()).toBeUndefined();
  });

  it('shows new data from a refresh', async () => {
    await loaded();

    facade.reload();
    pending().flush(summary(14));
    await settle();

    expect(facade.summary()?.official.energyKwh).toBe(14);
  });

  it('keeps the figures and warns when a refresh of the same query fails', async () => {
    await loaded();

    facade.reload();
    pending().flush({ detail: 'Servicio no disponible' }, { status: 503, statusText: 'Down' });
    await settle();

    expect(facade.summary()?.official.energyKwh).toBe(13);
    expect(facade.error()).toBeUndefined();
    expect(facade.refreshError()).toBe('Servicio no disponible');

    facade.reload();
    pending().flush(summary(13));
    await settle();
    expect(facade.refreshError()).toBeUndefined();
  });

  it('never presents figures of another filter as current when the new query fails', async () => {
    await loaded();

    facade.updateFilters({ network: 'KIA' });
    pending().flush(
      { detail: 'El periodo no puede superar 366 dias' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await settle();

    expect(facade.error()).toBe('El periodo no puede superar 366 dias');
    expect(facade.refreshError()).toBeUndefined();
  });

  it('discards a late response of the previous filter', async () => {
    await loaded();

    facade.reload();
    const late = pending();
    facade.updateFilters({ network: 'JAC' });
    const current = pending();

    expect(late.cancelled).toBe(true);
    expect(current.request.params.get('network')).toBe('JAC');
    current.flush(summary(5));
    await settle();
    expect(facade.summary()?.official.energyKwh).toBe(5);
  });

  it('refreshes while the person is active and stops after the inactivity cut-off', async () => {
    await loaded();

    await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS);
    pending().flush(summary(13));
    await settle();

    await vi.advanceTimersByTimeAsync(HUMAN_IDLE_MS);
    http
      .match((request) => request.url === SUMMARY_URL)
      .forEach((request) => request.flush(summary(13)));
    await settle();
    await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS);
    TestBed.tick();

    http.expectNone((request) => request.url === SUMMARY_URL);
  });
});
