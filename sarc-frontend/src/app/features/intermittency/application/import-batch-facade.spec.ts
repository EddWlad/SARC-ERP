import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { batch, row } from '../testing/import-fixtures';
import { IntermittencyImportBatchFacade } from './import-batch-facade';

const BATCH_URL = '/api/v1/intermittency/import-batches/b1';
const ROWS_URL = `${BATCH_URL}/rows`;

describe('IntermittencyImportBatchFacade', () => {
  let facade: IntermittencyImportBatchFacade;
  let http: HttpTestingController;

  const page = (rows = [row()]) => ({
    content: rows,
    page: 0,
    size: 50,
    totalElements: rows.length,
    totalPages: 1,
  });

  /** Entrega la respuesta y deja que el recurso y los efectos reaccionen. */
  async function answer(url: string, body: object): Promise<void> {
    TestBed.tick();
    http.expectOne((request) => request.url === url).flush(body);
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
  }

  /** Despues de un cambio se recarga el lote y la pagina visible. */
  async function reloaded(): Promise<void> {
    await answer(BATCH_URL, batch());
    await answer(ROWS_URL, page());
  }

  beforeEach(async () => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        IntermittencyImportBatchFacade,
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'b1' })) } },
      ],
    });
    facade = TestBed.inject(IntermittencyImportBatchFacade);
    http = TestBed.inject(HttpTestingController);
    await answer(BATCH_URL, batch());
    await answer(ROWS_URL, page());
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('carga el lote y sus filas', () => {
    expect(facade.batch()?.fileName).toBe('registro.xlsx');
    expect(facade.reviewable()).toBe(true);
    expect(facade.rows()).toHaveLength(1);
    expect(facade.rowsTotal()).toBe(1);
    expect(facade.canConfirm()).toBe(true);
  });

  it('corrige una fila con la version que leyo y recarga lo que cambia', async () => {
    const changing = facade.editRow(row(), {
      edits: [{ target: 'window', pair: 1, endsNextDay: true }],
    });
    const request = http.expectOne(`${ROWS_URL}/r1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      version: 0,
      edits: [{ target: 'window', pair: 1, endsNextDay: true }],
    });
    request.flush({ row: row({ version: 1 }), batch: batch() });

    expect((await changing).row.version).toBe(1);
    // Una edicion puede reclasificar otras filas del mismo punto: recarga el lote y la pagina.
    await reloaded();
    expect(facade.busy()).toBe(false);
  });

  it('vincula la fila a un punto', async () => {
    const linking = facade.linkRow(row({ classification: 'PENDING_LINK' }), 'p1');
    const request = http.expectOne(`${ROWS_URL}/r1/link`);
    expect(request.request.body).toEqual({ version: 0, equipmentId: 'p1' });
    request.flush({ row: row({ version: 1 }), batch: batch() });

    await linking;
    await reloaded();
  });

  it('decide la fila con sus resoluciones por ventana', async () => {
    const deciding = facade.decideRow(row(), {
      decision: 'APPLY',
      windows: [{ pair: 4, resolution: 'OMIT' }],
    });
    const request = http.expectOne(`${ROWS_URL}/r1/decision`);
    expect(request.request.body).toEqual({
      version: 0,
      decision: 'APPLY',
      windows: [{ pair: 4, resolution: 'OMIT' }],
    });
    request.flush({ row: row({ version: 1 }), batch: batch() });

    await deciding;
    await reloaded();
  });

  it('un error del servidor llega con su mensaje y no recarga nada', async () => {
    const failing = facade.decideRow(row(), { decision: 'APPLY' });
    http
      .expectOne(`${ROWS_URL}/r1/decision`)
      .flush(
        { code: 'CONCURRENT_MODIFICATION', detail: 'La fila fue modificada por otro usuario' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(failing).rejects.toThrow('La fila fue modificada por otro usuario');
    expect(facade.busy()).toBe(false);
  });

  it('confirma con la version del lote', async () => {
    const confirming = facade.confirm();
    const request = http.expectOne(`${BATCH_URL}/confirm`);
    expect(request.request.body).toEqual({ version: 2 });
    request.flush(batch({ status: 'CONFIRMING', version: 3 }));

    expect((await confirming).status).toBe('CONFIRMING');
    expect(facade.inProgress()).toBe(true);
  });
});
