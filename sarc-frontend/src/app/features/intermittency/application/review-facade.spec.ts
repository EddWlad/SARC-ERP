import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PendingRowResponse } from '../../../core/api/generated';
import { row } from '../testing/import-fixtures';
import { windowPage, windowResponse } from '../testing/window-fixtures';
import { ReviewFacade } from './review-facade';

const WINDOWS = '/api/v1/intermittency/windows';
const PENDING = '/api/v1/intermittency/pending-rows';

const REVIEW = windowResponse({ id: 'w1', state: 'REVIEW', reviewReasons: ['LONG_WINDOW'] });
const OPEN_WINDOW = windowResponse({ id: 'w2', state: 'OPEN', endAt: null, durationMinutes: null });

describe('ReviewFacade', () => {
  let facade: ReviewFacade;
  let http: HttpTestingController;

  /** Responde las cuatro consultas de una carga: por revisar, abiertas, prueba y pendientes. */
  async function answerLoad(
    loading: Promise<void>,
    answers: { review?: number; open?: number; test?: number; pending?: number } = {},
  ): Promise<void> {
    const windows = http.match((request) => request.url === WINDOWS);
    expect(windows).toHaveLength(3);
    for (const request of windows) {
      const state = request.request.params.get('states');
      const content = state === 'REVIEW' ? [REVIEW] : [];
      const total = {
        REVIEW: answers.review ?? 1,
        OPEN: answers.open ?? 0,
        TEST: answers.test ?? 0,
      }[state as 'REVIEW' | 'OPEN' | 'TEST'];
      request.flush(windowPage(content, { totalElements: total }));
    }
    http
      .expectOne((request) => request.url === PENDING)
      .flush({ content: [], page: 0, size: 1, totalElements: answers.pending ?? 0, totalPages: 0 });
    await loading;
  }

  /** Despues de un cambio la fachada recarga todo: espera las cuatro consultas y las responde vacias. */
  async function settleReload(): Promise<void> {
    const windows: TestRequest[] = [];
    const pending: TestRequest[] = [];
    await vi.waitFor(() => {
      windows.push(...http.match((request) => request.url === WINDOWS));
      pending.push(...http.match((request) => request.url === PENDING));
      expect(windows).toHaveLength(3);
      expect(pending).toHaveLength(1);
    });
    windows.forEach((request) => request.flush(windowPage([])));
    pending.forEach((request) =>
      request.flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 }),
    );
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ReviewFacade],
    });
    facade = TestBed.inject(ReviewFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carga la seccion elegida y la cantidad de cada una', async () => {
    await answerLoad(facade.load(), { review: 1, open: 2, test: 3, pending: 4 });

    expect(facade.windows()).toEqual([REVIEW]);
    expect(facade.counts()).toEqual({ REVIEW: 1, OPEN: 2, TEST: 3, PENDING: 4 });
    expect(facade.empty()).toBe(false);
  });

  it('una respuesta tardia de otra seccion no pisa la actual', async () => {
    const first = facade.load();
    const stale = http.match((request) => request.url === WINDOWS);
    const stalePending = http.expectOne((request) => request.url === PENDING);
    const second = facade.select('OPEN');
    const current = http.match((request) => request.url === WINDOWS);
    const currentPending = http.expectOne((request) => request.url === PENDING);

    for (const request of current) {
      const open = request.request.params.get('states') === 'OPEN';
      request.flush(windowPage(open ? [OPEN_WINDOW] : [], { totalElements: open ? 1 : 0 }));
    }
    currentPending.flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    await second;
    for (const request of stale) {
      const review = request.request.params.get('states') === 'REVIEW';
      request.flush(windowPage(review ? [REVIEW] : [], { totalElements: review ? 1 : 0 }));
    }
    stalePending.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    await first;

    expect(facade.section()).toBe('OPEN');
    expect(facade.windows()).toEqual([OPEN_WINDOW]);
    expect(facade.loading()).toBe(false);
  });

  it('confirmar la revision envia la version y la justificacion, y vuelve a cargar', async () => {
    const confirming = facade.confirmReview(REVIEW, 'Se verificó con el operador');
    const request = http.expectOne(`${WINDOWS}/w1/confirm-review`);
    expect(request.request.body).toEqual({
      version: 2,
      justification: 'Se verificó con el operador',
    });
    request.flush(windowResponse());
    // Tras el cambio se recarga todo para que las cantidades sigan al dia.
    await settleReload();
    await confirming;
  });

  it('un conflicto de version llega como error con el mensaje del servidor y no recarga', async () => {
    const annulling = facade.annul(REVIEW, 'Duplicada');
    http
      .expectOne(`${WINDOWS}/w1/annul`)
      .flush(
        { code: 'CONCURRENT_MODIFICATION', detail: 'La ventana cambió; recárguela.' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(annulling).rejects.toThrow('La ventana cambió; recárguela.');
    http.expectNone((request) => request.url === WINDOWS);
  });

  it('corregir envia solo lo cambiado con la version leida', async () => {
    const correcting = facade.correct(REVIEW, {
      endAt: '2026-10-05T14:00:00-05:00',
      justification: 'Fin real',
    });
    const request = http.expectOne(`${WINDOWS}/w1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      version: 2,
      endAt: '2026-10-05T14:00:00-05:00',
      justification: 'Fin real',
    });
    request.flush(windowResponse());
    await settleReload();
    await correcting;
  });

  it('cuenta lo que marcaria la prueba por punto: confirmadas y por revisar, y las abiertas sin marcar', async () => {
    const preview = facade.previewPointTest(
      { id: 'p1', name: 'Punto A', adminStatus: 'ACTIVE' },
      '2026-10-01',
      '2026-10-15',
    );
    const requests = http.match((request) => request.url === WINDOWS);
    expect(requests).toHaveLength(2);
    for (const request of requests) {
      expect(request.request.params.get('to')).toBe('2026-10-16');
      const open = request.request.params.get('states') === 'OPEN';
      request.flush(windowPage([], { totalElements: open ? 2 : 7 }));
    }

    expect(await preview).toEqual({ willMark: 7, openNotMarked: 2 });
  });

  it('marcar prueba por punto pide el rango con fin exclusivo y devuelve lo que informo el servidor', async () => {
    const marking = facade.pointTest(
      { id: 'p1', name: 'Punto A', adminStatus: 'ACTIVE' },
      true,
      '2026-10-01',
      '2026-10-15',
      'Pruebas del proveedor',
    );
    const request = http.expectOne('/api/v1/intermittency/points/p1/test');
    expect(request.request.body).toEqual({
      from: '2026-10-01',
      to: '2026-10-16',
      justification: 'Pruebas del proveedor',
    });
    request.flush({ affectedWindows: 5, openWindowsNotMarked: 1 });
    await settleReload();

    expect(await marking).toEqual({ affectedWindows: 5, openWindowsNotMarked: 1 });
  });

  describe('resolver filas pendientes', () => {
    const pending: PendingRowResponse = {
      batchId: 'b1',
      fileName: 'registro.xlsx',
      batchCreatedAt: '2026-10-06T14:00:00Z',
      row: row({ id: 'r9', version: 4, classification: 'PENDING_LINK' }),
    };

    it('el vinculo viaja junto con la decision en una sola resolucion', async () => {
      const actions = facade.rowActions(pending);
      const linked = await actions.linkRow(pending.row, 'p7');
      expect(linked.row.equipmentId).toBe('p7');
      http.expectNone(() => true);

      const deciding = actions.decideRow(linked.row, {
        decision: 'APPLY',
        acknowledgeTotal: false,
        windows: [],
      });
      const request = http.expectOne('/api/v1/intermittency/import-batches/b1/rows/r9/resolve');
      expect(request.request.body).toEqual({
        version: 4,
        decision: 'APPLY',
        acknowledgeTotal: false,
        windows: [],
        equipmentId: 'p7',
      });
      request.flush({ row: pending.row, batch: {} });
      await settleReload();
      await deciding;
    });

    it('la fila de un lote confirmado no se edita', async () => {
      await expect(facade.rowActions(pending).editRow(pending.row, {})).rejects.toThrow(
        'ya se confirmó',
      );
    });
  });
});
