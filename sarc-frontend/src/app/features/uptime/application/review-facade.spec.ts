import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ReviewFacade } from './review-facade';

const LIST_URL = '/api/v1/uptime/measurements';
const BATCH_URL = '/api/v1/uptime/measurements/validation';

const item = (id: string, observations: string[] = []) => ({
  id,
  version: 1,
  observations,
  validatable: true,
});

const page = (content: unknown[]) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
});

describe('ReviewFacade', () => {
  let facade: ReviewFacade;
  let http: HttpTestingController;

  async function load(content: unknown[]) {
    const loading = facade.load();
    http.expectOne((request) => request.url === LIST_URL).flush(page(content));
    await loading;
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [ReviewFacade, provideHttpClient(), provideHttpClientTesting()],
    });
    facade = TestBed.inject(ReviewFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide la cola elegida', async () => {
    facade.queue.set('OBSERVED');
    const loading = facade.load();
    const request = http.expectOne((call) => call.url === LIST_URL);
    expect(request.request.params.get('queue')).toBe('OBSERVED');
    request.flush(page([]));
    await loading;
  });

  it('seleccionar todo deja fuera las observadas', async () => {
    await load([item('a'), item('b', ['SUM_ZERO']), item('c')]);

    facade.selectAll();

    expect([...facade.selected()]).toEqual(['a', 'c']);
  });

  it('no permite marcar una observada', async () => {
    await load([item('b', ['SUM_ZERO'])]);

    facade.toggle('b');

    expect(facade.selected().size).toBe(0);
  });

  it('divide en bloques de 500 y suma resultados y omitidas', async () => {
    const many = Array.from({ length: 501 }, (_, index) => item(`m${index}`));
    await load(many);
    facade.selectAll();

    const validating = facade.validateSelected();
    const first = http.expectOne(BATCH_URL);
    expect(first.request.body.items).toHaveLength(500);
    first.flush({ validated: 499, skipped: [{ id: 'm3', reason: 'STALE_VERSION' }] });
    await Promise.resolve();
    await Promise.resolve();
    const second = http.expectOne(BATCH_URL);
    expect(second.request.body.items).toHaveLength(1);
    second.flush({ validated: 1, skipped: [] });
    await vi.waitFor(() => http.expectOne((request) => request.url === LIST_URL).flush(page([])));
    await validating;

    expect(facade.result()).toEqual({
      validated: 500,
      skipped: [{ id: 'm3', reason: 'STALE_VERSION' }],
    });
    expect(facade.selected().size).toBe(0);
  });

  it('un error de lote conserva la seleccion y lo avisa', async () => {
    await load([item('a')]);
    facade.selectAll();

    const validating = facade.validateSelected();
    http
      .expectOne(BATCH_URL)
      .flush({ detail: 'Sin permiso' }, { status: 403, statusText: 'Forbidden' });
    await validating;

    expect(facade.error()).not.toBe('');
    expect(facade.selected().size).toBe(1);
  });
  it('la conciliación temporal no obliga a revalidar observaciones de valores', async () => {
    facade.queue.set('TEMPORAL_RECONCILIATION' as never);
    await load([
      { ...item('temporal', ['DURATION_INVALID']), temporalPrecision: 'REQUIRES_RECONCILIATION' },
    ]);
    facade.selectAll();
    expect([...facade.selected()]).toEqual(['temporal']);
  });

  it('repite una solicitud fallida con la misma clave y lee el resultado durable', async () => {
    facade.queue.set('TEMPORAL_RECONCILIATION');
    await load([item('a')]);
    facade.selectAll();
    const first = facade.transitionSelected();
    const initial = http.expectOne(LIST_URL + '/temporal-transition');
    const key = initial.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    initial.flush(
      { detail: 'Temporalmente indisponible' },
      { status: 503, statusText: 'Unavailable' },
    );
    await first;
    const second = facade.transitionSelected();
    const repeated = http.expectOne(LIST_URL + '/temporal-transition');
    expect(repeated.request.headers.get('Idempotency-Key')).toBe(key);
    repeated.flush({ workId: 'work', status: 'QUEUED', version: 0 });
    await vi.waitFor(() =>
      http
        .expectOne((request) => request.url === LIST_URL + '/temporal-transition/work')
        .flush({
          workId: 'work',
          status: 'COMPLETED',
          version: 3,
          processed: 1,
          total: 1,
          results: page([{ id: 'a', outcome: 'STALE_VERSION' }]),
        }),
    );
    await second;
    expect(facade.work()?.processed).toBe(1);
    expect(facade.work()?.status).toBe('COMPLETED');
  });
  it('detiene la consulta periódica al salir', async () => {
    vi.useFakeTimers();
    facade.workId.set('work');
    const reading = facade.readWork();
    http
      .expectOne((request) => request.url === LIST_URL + '/temporal-transition/work')
      .flush({ workId: 'work', status: 'RUNNING', version: 1 });
    await reading;
    TestBed.resetTestingModule();
    await vi.advanceTimersByTimeAsync(3000);
    http.expectNone((request) => request.url.includes('temporal-transition'));
    vi.useRealTimers();
  });
});
