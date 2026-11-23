import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TicketPageResponse, TicketSummaryResponse } from '../../../core/api/generated';
import { TicketsFacade } from './tickets-facade';

const URL = '/api/v1/incidents/tickets';

function summary(code: string): TicketSummaryResponse {
  return {
    id: `id-${code}`,
    code,
    state: 'OPEN',
    elementClass: 'EQUIPMENT',
    elementLabel: 'Equipo',
    incidentType: 'DISCONNECTION',
    incidentTypeLabel: 'Desconexión',
    priority: 'HIGH',
    responsible: { id: 'u1', name: 'Ana' },
    t0: '2026-11-21T15:00:00Z',
    lastActivityAt: '2026-11-21T15:00:00Z',
    episodeCount: 1,
    version: 0,
  };
}

function page(codes: string[], extra: Partial<TicketPageResponse> = {}): TicketPageResponse {
  return {
    items: codes.map(summary),
    total: codes.length,
    page: 0,
    size: 20,
    cutOff: '2026-11-21T15:00:00Z',
    ...extra,
  };
}

describe('TicketsFacade', () => {
  let http: HttpTestingController;
  let facade: TicketsFacade;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TicketsFacade],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(TicketsFacade);
  });

  afterEach(() => http.verify());

  it('carga la primera pagina con el orden por defecto y sin filtros vacios', async () => {
    const loading = facade.load();
    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('sort')).toBe('lastActivityAt,desc');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('state')).toBe(false);
    expect(request.request.params.has('search')).toBe(false);
    request.flush(page(['TICKET-9002', 'TICKET-9001']));
    await loading;

    expect(facade.items().map((item) => item.code)).toEqual(['TICKET-9002', 'TICKET-9001']);
    expect(facade.total()).toBe(2);
    expect(facade.refresh.lastUpdatedAt()).not.toBeNull();
    expect(facade.loading()).toBe(false);
  });

  it('un filtro vuelve a la primera pagina y se envia', async () => {
    facade.goToPage(3);
    http.expectOne((r) => r.url === URL).flush(page([]));
    await Promise.resolve();

    facade.filter('priority', 'CRITICAL');
    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('priority')).toBe('CRITICAL');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page(['TICKET-9001']));
    await Promise.resolve();
    expect(facade.hasFilters()).toBe(true);
  });

  it('ordenar por el mismo campo invierte la direccion y otro campo empieza ascendente', async () => {
    facade.sortBy('code');
    expect(http.expectOne((r) => r.url === URL).request.params.get('sort')).toBe('code,asc');
    facade.sortBy('code');
    expect(http.expectOne((r) => r.url === URL).request.params.get('sort')).toBe('code,desc');
    facade.sortBy('priority');
    const last = http.expectOne((r) => r.url === URL);
    expect(last.request.params.get('sort')).toBe('priority,asc');
    last.flush(page([]));
    await Promise.resolve();
  });

  it('muestra el aviso de un codigo anterior al numero inicial', async () => {
    facade.searchFor('TICKET-085');
    http.expectOne((r) => r.url === URL).flush(page([], { notice: 'TICKET_BEFORE_START' }));
    await Promise.resolve();
    await Promise.resolve();
    expect(facade.notice()).toBe('TICKET_BEFORE_START');
    expect(facade.items()).toEqual([]);
  });

  it('si la primera carga falla no hay nada que mostrar', async () => {
    const loading = facade.load();
    http
      .expectOne((r) => r.url === URL)
      .flush({ code: 'X', detail: 'No se pudo cargar' }, { status: 500, statusText: 'Error' });
    await loading;
    expect(facade.failed()).toBe(true);
    expect(facade.refreshFailed()).toBe(false);
    expect(facade.errorMessage()).toBe('No se pudo cargar');
  });

  it('si una actualizacion falla conserva lo mostrado y avisa de la conectividad', async () => {
    const first = facade.load();
    http.expectOne((r) => r.url === URL).flush(page(['TICKET-9001']));
    await first;

    const second = facade.load();
    http.expectOne((r) => r.url === URL).error(new ProgressEvent('error'), { status: 0 });
    await second;

    expect(facade.items().map((item) => item.code)).toEqual(['TICKET-9001']);
    expect(facade.refreshFailed()).toBe(true);
    expect(facade.failed()).toBe(false);
    expect(facade.refresh.connectionLost()).toBe(true);
    expect(facade.refresh.lastUpdatedAt()).not.toBeNull();
  });

  it('limpiar los filtros consulta de nuevo sin ellos', async () => {
    facade.filter('state', 'OPEN');
    http.expectOne((r) => r.url === URL).flush(page([]));
    await Promise.resolve();
    facade.clearFilters();
    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.has('state')).toBe(false);
    request.flush(page([]));
    await Promise.resolve();
    expect(facade.hasFilters()).toBe(false);
  });
});
