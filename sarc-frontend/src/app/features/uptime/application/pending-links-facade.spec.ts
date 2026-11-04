import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UptimeImportRowResponse } from '../../../core/api/generated';
import { PendingLinksFacade } from './pending-links-facade';

const BASE = '/api/v1/uptime';
const row = { id: 'r1', version: 2, sourceConnector: 'A- PUESTO 6' } as UptimeImportRowResponse;
const page = (content: unknown[]) => ({
  content,
  page: 0,
  size: 50,
  totalElements: content.length,
  totalPages: 1,
});

describe('PendingLinksFacade', () => {
  let facade: PendingLinksFacade;
  let http: HttpTestingController;

  function answerLoad() {
    http.expectOne((call) => call.url === `${BASE}/pending-links`).flush(page([row]));
    http.expectOne((call) => call.url === `${BASE}/connector-aliases`).flush(page([]));
    http.expectOne((call) => call.url === `${BASE}/scope/connectors`).flush([]);
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [PendingLinksFacade, provideHttpClient(), provideHttpClientTesting()],
    });
    facade = TestBed.inject(PendingLinksFacade);
    http = TestBed.inject(HttpTestingController);
    const loading = facade.load();
    answerLoad();
    await loading;
  });

  afterEach(() => http.verify());

  it('vincula con version y aprobacion de etiqueta y recarga', async () => {
    const linking = facade.link(row, 'c1', true);
    const request = http.expectOne(`${BASE}/pending-links/r1/link`);
    expect(request.request.body).toEqual({ version: 2, connectorId: 'c1', rememberLabel: true });
    request.flush({ ...row, outcome: 'LINKED' });
    await vi.waitFor(answerLoad);

    expect(await linking).toBe(true);
  });

  it('sin aprobar la etiqueta no crea alias', async () => {
    const linking = facade.link(row, 'c1', false);
    const request = http.expectOne(`${BASE}/pending-links/r1/link`);
    expect(request.request.body.rememberLabel).toBe(false);
    request.flush({ ...row, outcome: 'LINKED' });
    await vi.waitFor(answerLoad);
    await linking;
  });

  it('un conflicto de version recarga y explica', async () => {
    const linking = facade.link(row, 'c1', false);
    http
      .expectOne(`${BASE}/pending-links/r1/link`)
      .flush(
        { detail: 'La fila cambio.', code: 'CONCURRENT_MODIFICATION' },
        { status: 409, statusText: 'Conflict' },
      );
    await vi.waitFor(answerLoad);

    expect(await linking).toBe(false);
    expect(facade.error()).toContain('La fila cambio');
  });
});
