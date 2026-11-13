import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { KiaPointResponse } from '../../../core/api/generated';
import { windowDetail, windowPage, windowResponse } from '../testing/window-fixtures';
import { WindowsFacade } from './windows-facade';

const URL = '/api/v1/intermittency/windows';

const POINT: KiaPointResponse = {
  id: 'p1',
  name: 'Punto A',
  assignedCode: '900001',
  adminStatus: 'ACTIVE',
};

describe('WindowsFacade', () => {
  let facade: WindowsFacade;
  let http: HttpTestingController;

  const list = () => http.expectOne((request) => request.url === URL);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), WindowsFacade],
    });
    facade = TestBed.inject(WindowsFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carga la primera pagina sin filtros', async () => {
    const loading = facade.load();
    const request = list();
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('equipmentIds')).toBe(false);
    request.flush(windowPage([windowResponse()], { totalElements: 41 }));
    await loading;

    expect(facade.items()).toHaveLength(1);
    expect(facade.total()).toBe(41);
    expect(facade.empty()).toBe(false);
  });

  it('el ultimo dia elegido se incluye: la API recibe el dia siguiente como fin exclusivo', async () => {
    const filtering = facade.filter({
      point: POINT,
      firstDay: '2026-10-01',
      lastDay: '2026-10-15',
      states: ['REVIEW', 'TEST'],
    });
    const request = list();
    expect(request.request.params.get('equipmentIds')).toBe('p1');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-16');
    expect(request.request.params.getAll('states')).toEqual(['REVIEW', 'TEST']);
    request.flush(windowPage([]));
    await filtering;

    expect(facade.empty()).toBe(true);
  });

  it('cambiar un filtro vuelve a la primera pagina', async () => {
    const paging = facade.changePage(3, 50);
    list().flush(windowPage([]));
    await paging;
    expect(facade.pageIndex()).toBe(3);

    const filtering = facade.filter({ firstDay: '2026-10-01' });
    const request = list();
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('50');
    request.flush(windowPage([]));
    await filtering;
  });

  it('una respuesta tardia de un filtro anterior no pisa la actual', async () => {
    const first = facade.load();
    const stale = list();
    const second = facade.filter({ firstDay: '2026-10-10' });
    const current = list();

    current.flush(windowPage([windowResponse({ id: 'nueva' })]));
    await second;
    stale.flush(windowPage([windowResponse({ id: 'vieja' })]));
    await first;

    expect(facade.items().map((window) => window.id)).toEqual(['nueva']);
    expect(facade.loading()).toBe(false);
  });

  it('con un error muestra el mensaje y deja la lista vacia', async () => {
    const loading = facade.load();
    list().flush(
      { code: 'X', detail: 'Servicio no disponible.' },
      { status: 503, statusText: 'x' },
    );
    await loading;

    expect(facade.error()).toBe('Servicio no disponible.');
    expect(facade.items()).toEqual([]);
    expect(facade.empty()).toBe(false);
  });

  it('abre el detalle con el historial de revisiones', async () => {
    const opening = facade.openDetail('w1');
    http.expectOne(`${URL}/w1`).flush(windowDetail());
    const detail = await opening;

    expect(detail?.revisions).toHaveLength(2);
    expect(facade.detail()).toBe(detail);
  });

  it('si el detalle no se puede abrir informa el motivo', async () => {
    const opening = facade.openDetail('w1');
    http
      .expectOne(`${URL}/w1`)
      .flush(
        { code: 'NOT_FOUND', detail: 'No se encontró la ventana.' },
        { status: 404, statusText: 'x' },
      );

    expect(await opening).toBeNull();
    expect(facade.detailError()).toBe('No se encontró la ventana.');
  });
});
