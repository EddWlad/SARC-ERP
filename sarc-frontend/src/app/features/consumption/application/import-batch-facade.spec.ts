import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ImportStatus } from '../../../shared/imports/import-batch';
import { ImportBatch } from '../domain/energy-import';
import { ImportBatchFacade } from './import-batch-facade';

const BATCH_URL = '/api/v1/energy/imports/b-1';

function batch(status: ImportStatus): ImportBatch {
  return {
    id: 'b-1',
    source: 'WALLBOX',
    scope: 'wallbox',
    origin: 'FILE',
    fileName: 'wallbox.xlsx',
    checksum: 'abc',
    status,
    totalRows: 2,
    newRows: 2,
    identicalRows: 0,
    modifiedRows: 0,
    equipmentChangedRows: 0,
    invalidRows: 0,
    duplicateRows: 0,
    pendingLinkRows: 0,
    appliedRows: 0,
    createdAt: '2026-10-23T10:00:00Z',
    version: 1,
  };
}

describe('ImportBatchFacade', () => {
  let facade: ImportBatchFacade;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ImportBatchFacade,
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'b-1' })) } },
      ],
    });
    facade = TestBed.inject(ImportBatchFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  /** Entrega la respuesta y deja que el recurso y los efectos reaccionen. */
  async function answer(url: string, body: object): Promise<void> {
    TestBed.tick();
    http.expectOne((request) => request.url === url).flush(body);
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
  }

  it('keeps polling while the batch is parsing and loads the rows once it is ready', async () => {
    await answer(BATCH_URL, batch('PARSING'));
    expect(facade.inProgress()).toBe(true);
    http.expectNone(`${BATCH_URL}/rows`);

    await vi.advanceTimersByTimeAsync(1500);
    await answer(BATCH_URL, batch('PARSING'));

    await vi.advanceTimersByTimeAsync(1500);
    await answer(BATCH_URL, batch('READY'));

    expect(facade.reviewable()).toBe(true);
    await answer(`${BATCH_URL}/rows`, { content: [], page: 0, size: 50, totalElements: 0 });
    await vi.advanceTimersByTimeAsync(3000);
    http.expectNone(BATCH_URL);
  });

  it('does not allow confirming while the rows could not be loaded', async () => {
    // QA-11: sin filas no hay revision; confirmar a ciegas aplicaria lo que nadie vio.
    await answer(BATCH_URL, batch('READY'));
    TestBed.tick();
    http
      .expectOne((request) => request.url === `${BATCH_URL}/rows`)
      .flush(null, { status: 500, statusText: 'Server Error' });
    await vi.advanceTimersByTimeAsync(0);

    expect(facade.reviewable()).toBe(true);
    expect(facade.rowsFailed()).toBe(true);
    expect(facade.canConfirm()).toBe(false);

    facade.reloadRows();
    await answer(`${BATCH_URL}/rows`, { content: [], page: 0, size: 50, totalElements: 0 });
    expect(facade.canConfirm()).toBe(true);
  });

  it('retries a transient polling failure with a growing delay', async () => {
    // OPT-07: un corte breve no detiene el seguimiento del lote.
    await answer(BATCH_URL, batch('PARSING'));
    await vi.advanceTimersByTimeAsync(1500);
    TestBed.tick();
    http.expectOne(BATCH_URL).flush(null, { status: 503, statusText: 'Unavailable' });
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    expect(facade.failed()).toBe(true);
    expect(facade.reconnecting()).toBe(true);

    await vi.advanceTimersByTimeAsync(1500);
    TestBed.tick();
    http.expectOne(BATCH_URL).flush(null, { status: 0, statusText: 'Unknown Error' });
    await vi.advanceTimersByTimeAsync(1500);
    TestBed.tick();
    http.expectNone(BATCH_URL);

    await vi.advanceTimersByTimeAsync(1500);
    await answer(BATCH_URL, batch('READY'));
    expect(facade.failed()).toBe(false);
    expect(facade.reviewable()).toBe(true);
    await answer(`${BATCH_URL}/rows`, { content: [], page: 0, size: 50, totalElements: 0 });
  });

  it('reports a missing batch apart from other failures', async () => {
    TestBed.tick();
    http.expectOne(BATCH_URL).flush(null, { status: 404, statusText: 'Not Found' });
    await vi.advanceTimersByTimeAsync(0);

    expect(facade.notFound()).toBe(true);
    expect(facade.failed()).toBe(false);
  });
});
