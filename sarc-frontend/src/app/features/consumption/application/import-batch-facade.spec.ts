import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ImportBatch, ImportStatus } from '../domain/energy-import';
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

  it('reports a missing batch apart from other failures', async () => {
    TestBed.tick();
    http.expectOne(BATCH_URL).flush(null, { status: 404, statusText: 'Not Found' });
    await vi.advanceTimersByTimeAsync(0);

    expect(facade.notFound()).toBe(true);
    expect(facade.failed()).toBe(false);
  });
});
