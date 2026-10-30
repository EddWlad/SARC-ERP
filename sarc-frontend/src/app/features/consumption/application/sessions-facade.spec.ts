import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SessionsFacade } from './sessions-facade';

const SESSIONS_URL = '/api/v1/energy/sessions';
const SUMMARY_URL = '/api/v1/energy/summary';

describe('SessionsFacade', () => {
  let facade: SessionsFacade;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), SessionsFacade],
    });
    facade = TestBed.inject(SessionsFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('resume el histórico con el período y filtros de la lista, sin estado ni búsqueda', async () => {
    facade.filterState('PENDING_LINK');
    facade.updateFilters({ network: 'JAC' });
    TestBed.tick();

    const list = http.expectOne((request) => request.url === SESSIONS_URL);
    const summary = http.expectOne((request) => request.url === SUMMARY_URL);

    expect(list.request.params.get('state')).toBe('PENDING_LINK');
    expect(summary.request.params.get('network')).toBe('JAC');
    expect(summary.request.params.get('from')).toBe(list.request.params.get('from'));
    expect(summary.request.params.has('state')).toBe(false);
    expect(summary.request.params.has('text')).toBe(false);

    list.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    summary.flush({
      official: { sessions: 5, energyKwh: 3.75, equipment: 1, sessionsWithoutEnergy: 2 },
    });
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();

    expect(facade.summary()?.official.energyKwh).toBe(3.75);
    expect(facade.summary()?.official.sessionsWithoutEnergy).toBe(2);
  });
});
