import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { TicketPageResponse, TicketSummaryResponse } from '../../../core/api/generated';
import { FileSaver } from '../../../core/files/file-saver';
import { ControlFacade } from './control-facade';

const URL = '/api/v1/incidents/tickets';
const REPORT_URL = '/api/v1/incidents/reports/control';

function summary(
  code: string,
  overrides: Partial<TicketSummaryResponse> = {},
): TicketSummaryResponse {
  return {
    id: `id-${code}`,
    code,
    state: 'OPEN',
    elementClass: 'EQUIPMENT',
    elementLabel: 'Electrolinera X / Conectores 1 y 2',
    incidentType: 'DISCONNECTION',
    incidentTypeLabel: 'Desconexión',
    priority: 'HIGH',
    responsible: { id: 'u1', name: 'Ana' },
    t0: '2026-11-21T15:00:00Z',
    lastActivityAt: '2026-11-21T15:00:00Z',
    episodeCount: 1,
    version: 0,
    ...overrides,
  };
}

function page(items: TicketSummaryResponse[], total = items.length): TicketPageResponse {
  return { items, total, page: 0, size: 20, cutOff: '2026-11-22T20:30:00Z' };
}

describe('ControlFacade', () => {
  let http: HttpTestingController;
  let facade: ControlFacade;
  const saver = { download: vi.fn().mockResolvedValue(undefined) };

  beforeEach(() => {
    saver.download.mockClear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileSaver, useValue: saver },
        ControlFacade,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(ControlFacade);
  });

  afterEach(() => http.verify());

  /** El efecto del constructor pide la primera carga; la lista y el conteo de abiertos van en paralelo. */
  async function settle(first: TicketPageResponse, open = 0) {
    TestBed.tick();
    const requests = http.match((r) => r.url === URL);
    const list = requests.find((r) => r.request.params.get('size') === '20');
    const count = requests.find((r) => r.request.params.get('size') === '1');
    list?.flush(first);
    count?.flush(page([], open));
    await vi.waitFor(() => expect(facade.loading()).toBe(false));
    return { list, count };
  }

  it('abre en «Todo el historial»: sin fechas, ordenado por numero y con el conteo de abiertos aparte', async () => {
    const { list, count } = await settle(page([summary('TICKET-9001')], 3), 2);

    expect(list?.request.params.has('from')).toBe(false);
    expect(list?.request.params.has('to')).toBe(false);
    expect(list?.request.params.get('sort')).toBe('code,asc');
    expect(count?.request.params.get('state')).toBe('OPEN');
    expect(facade.total()).toBe(3);
    expect(facade.open()).toBe(2);
    expect(facade.closed()).toBe(1);
    expect(facade.cutOff()).toBe('2026-11-22T20:30:00Z');
  });

  it('el estado de cada fila es el del final del periodo cuando el servidor lo manda', async () => {
    await settle(
      page([
        summary('TICKET-9001', { state: 'CLOSED', stateAtPeriodEnd: 'OPEN' }),
        summary('TICKET-9002', { state: 'OPEN' }),
      ]),
    );

    expect(facade.rows().map((row) => row.state)).toEqual(['OPEN', 'OPEN']);
  });

  it('un periodo manda los dias inclusivos y vuelve a la primera pagina', async () => {
    await settle(page([]));

    facade.period.chooseRange({ from: '2026-10-01', to: '2026-11-01' });
    TestBed.tick();
    const requests = http.match((r) => r.url === URL);
    expect(requests.length).toBe(2);
    expect(requests[0].request.params.get('from')).toBe('2026-10-01');
    expect(requests[0].request.params.get('to')).toBe('2026-10-31');
    requests.forEach((request) => request.flush(page([])));
    await vi.waitFor(() => expect(facade.loading()).toBe(false));
    expect(facade.page()).toBe(0);
  });

  it('con un estado elegido no pide el conteo aparte: el total ya es de ese estado', async () => {
    await settle(page([]));

    facade.filter('state', 'OPEN');
    TestBed.tick();
    const requests = http.match((r) => r.url === URL);
    expect(requests.length).toBe(1);
    expect(requests[0].request.params.get('state')).toBe('OPEN');
    requests[0].flush(page([summary('TICKET-9001')], 5));
    await vi.waitFor(() => expect(facade.loading()).toBe(false));

    expect(facade.open()).toBe(5);
    expect(facade.closed()).toBe(0);
    expect(facade.hasFilters()).toBe(true);
  });

  it('descargar manda el mismo periodo y los mismos filtros que se ven', async () => {
    await settle(page([]));
    facade.filter('network', 'KIA');
    TestBed.tick();
    http.match((r) => r.url === URL).forEach((request) => request.flush(page([])));
    await vi.waitFor(() => expect(facade.loading()).toBe(false));

    const request = new HttpResponse<Blob>({ body: new Blob(['x']) });
    saver.download.mockImplementationOnce(async (source) => {
      source.subscribe();
      const call = http.expectOne((r) => r.url === REPORT_URL);
      expect(call.request.params.get('format')).toBe('PDF');
      expect(call.request.params.get('network')).toBe('KIA');
      expect(call.request.params.has('from')).toBe(false);
      call.flush(request.body);
    });

    await facade.downloadReport('PDF');

    expect(saver.download).toHaveBeenCalledOnce();
    expect(saver.download.mock.calls[0][1]).toBe('control-tickets.pdf');
    expect(facade.downloading()).toBeUndefined();
  });

  it('si la descarga falla rechaza y deja el boton libre', async () => {
    await settle(page([]));
    saver.download.mockRejectedValueOnce({ detail: 'El período admite hasta 366 días' });

    await expect(facade.downloadReport('XLSX')).rejects.toMatchObject({
      detail: 'El período admite hasta 366 días',
    });
    expect(facade.downloading()).toBeUndefined();
  });

  it('si la primera carga falla lo dice; si falla una actualizacion conserva las filas', async () => {
    TestBed.tick();
    http
      .match((r) => r.url === URL)
      .forEach((request) =>
        request.flush(
          { code: 'X', detail: 'No se pudo cargar' },
          { status: 500, statusText: 'Error' },
        ),
      );
    await vi.waitFor(() => expect(facade.failed()).toBe(true));
    expect(facade.errorMessage()).toBe('No se pudo cargar');

    const retry = facade.load();
    const retried = http.match((r) => r.url === URL);
    retried.forEach((request) => request.flush(page([summary('TICKET-9001')])));
    await retry;
    expect(facade.failed()).toBe(false);

    const refresh = facade.load();
    http
      .match((r) => r.url === URL)
      .forEach((request) =>
        request.flush({ code: 'X', detail: 'Caído' }, { status: 500, statusText: 'Error' }),
      );
    await refresh;
    expect(facade.refreshFailed()).toBe(true);
    expect(facade.rows().map((row) => row.code)).toEqual(['TICKET-9001']);
  });
});
