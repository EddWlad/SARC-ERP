import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ReviewedDayResponse } from '../../../core/api/generated';
import { ReviewedDaysFacade } from './reviewed-days-facade';

const URL = '/api/v1/intermittency/reviewed-days';

function day(date: string, overrides: Partial<ReviewedDayResponse> = {}): ReviewedDayResponse {
  return {
    day: date,
    active: false,
    finished: true,
    pendingLinkRows: 0,
    unconfirmedBatches: 0,
    ...overrides,
  };
}

describe('ReviewedDaysFacade', () => {
  let http: HttpTestingController;
  let facade: ReviewedDaysFacade;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-20T15:00:00-05:00'));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ReviewedDaysFacade],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(ReviewedDaysFacade);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  async function load(days: ReviewedDayResponse[]): Promise<void> {
    const loading = facade.load();
    const request = http.expectOne((candidate) => candidate.url === URL);
    expect(request.request.params.get('from')).toBe('2026-09-20');
    // El fin es exclusivo: llega hasta hoy incluido.
    expect(request.request.params.get('to')).toBe('2026-10-21');
    request.flush(days);
    await loading;
  }

  it('carga los ultimos 31 dias, hoy incluido, del mas reciente al mas antiguo', async () => {
    await load([day('2026-10-18'), day('2026-10-19'), day('2026-10-20', { finished: false })]);

    expect(facade.days().map((item) => item.day)).toEqual([
      '2026-10-20',
      '2026-10-19',
      '2026-10-18',
    ]);
    expect(facade.today()).toBe('2026-10-20');
    expect(facade.loading()).toBe(false);
    expect(facade.error()).toBe('');
  });

  it('marcar un dia lo pide y vuelve a cargar la lista; devuelve los lotes de los que avisar', async () => {
    await load([day('2026-10-19')]);

    const marking = facade.mark(day('2026-10-19'));
    const mark = http.expectOne({ method: 'PUT', url: `${URL}/2026-10-19` });
    mark.flush({
      day: day('2026-10-19', { active: true, version: 0 }),
      warnings: [{ batchId: 'b1', fileName: 'turno.csv' }],
    });
    await Promise.resolve();
    http
      .expectOne((candidate) => candidate.url === URL)
      .flush([day('2026-10-19', { active: true, version: 0 })]);
    const result = await marking;

    expect(result.warnings).toEqual([{ batchId: 'b1', fileName: 'turno.csv' }]);
    expect(facade.days()[0].active).toBe(true);
  });

  it('retirar la marca envia la version leida y la justificacion', async () => {
    await load([day('2026-10-19', { active: true, version: 3 })]);

    const withdrawing = facade.withdraw(
      day('2026-10-19', { active: true, version: 3 }),
      'Se marco por error',
    );
    const request = http.expectOne({ method: 'POST', url: `${URL}/2026-10-19/withdraw` });
    expect(request.request.body).toEqual({ version: 3, justification: 'Se marco por error' });
    request.flush(day('2026-10-19', { version: 4 }));
    await Promise.resolve();
    http.expectOne((candidate) => candidate.url === URL).flush([day('2026-10-19', { version: 4 })]);
    await withdrawing;

    expect(facade.days()[0].active).toBe(false);
  });

  it('un rechazo del servidor se propaga con su mensaje y no vuelve a cargar', async () => {
    await load([day('2026-10-19')]);

    const marking = facade.mark(day('2026-10-19'));
    const rejection = expect(marking).rejects.toThrow('Solo se marca un dia que ya termino');
    http
      .expectOne({ method: 'PUT', url: `${URL}/2026-10-19` })
      .flush(
        { code: 'DAY_NOT_FINISHED', detail: 'Solo se marca un dia que ya termino' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await rejection;

    http.expectNone((candidate) => candidate.url === URL);
  });

  it('si la carga falla muestra el mensaje del servidor', async () => {
    const loading = facade.load();
    http
      .expectOne((candidate) => candidate.url === URL)
      .flush(
        { code: 'ACCESS_DENIED', detail: 'No tiene permisos para esta operacion' },
        { status: 403, statusText: 'Forbidden' },
      );
    await loading;

    expect(facade.error()).toBe('No tiene permisos para esta operacion');
    expect(facade.days()).toEqual([]);
  });

  it('un mes pasado se muestra entero para marcar el historico, de la quincena 1 al fin de mes', async () => {
    const choosing = facade.chooseMonth('2026-08');
    const request = http.expectOne((candidate) => candidate.url === URL);
    expect(request.request.params.get('from')).toBe('2026-08-01');
    expect(request.request.params.get('to')).toBe('2026-09-01');
    request.flush([day('2026-08-30'), day('2026-08-31')]);
    await choosing;

    expect(facade.month()).toBe('2026-08');
    expect(facade.days().map((item) => item.day)).toEqual(['2026-08-31', '2026-08-30']);
  });

  it('el mes en curso llega solo hasta hoy incluido y volver a los ultimos 31 dias limpia el mes', async () => {
    const current = facade.chooseMonth('2026-10');
    const month = http.expectOne((candidate) => candidate.url === URL);
    expect(month.request.params.get('from')).toBe('2026-10-01');
    expect(month.request.params.get('to')).toBe('2026-10-21');
    month.flush([]);
    await current;

    const recent = facade.showRecent();
    const request = http.expectOne((candidate) => candidate.url === URL);
    expect(request.request.params.get('from')).toBe('2026-09-20');
    request.flush([]);
    await recent;

    expect(facade.month()).toBe('');
  });

  it('un mes posterior al vigente se acota al vigente y no pide un rango invertido', async () => {
    const choosing = facade.chooseMonth('2027-01');
    const request = http.expectOne((candidate) => candidate.url === URL);
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-21');
    request.flush([]);
    await choosing;

    expect(facade.month()).toBe('2026-10');
  });
});
