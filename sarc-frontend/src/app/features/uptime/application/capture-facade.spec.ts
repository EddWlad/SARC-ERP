import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CaptureFacade } from './capture-facade';

const GRID_URL = '/api/v1/uptime/grid';
const MEASUREMENTS_URL = '/api/v1/uptime/measurements';

const row = (connectorId: string, situation = 'NONE') => ({
  connectorId,
  ordinal: 1,
  connectorTypeCode: 'CCS2',
  connectorTypeName: 'CCS Combo 2 DC',
  connectorActive: true,
  equipmentId: 'e1',
  equipmentName: 'Equipo',
  network: 'JAC',
  type: 'CHARGING_STATION',
  brand: 'BENNY',
  equipmentStatus: 'ACTIVE',
  situation,
});

describe('CaptureFacade', () => {
  let facade: CaptureFacade;
  let http: HttpTestingController;

  const typeDraft = () =>
    facade.updateDraft({ available: '1296', occupied: '105', fault: '0', offline: '39' });

  async function loadGrid(rows = [row('c1'), row('c2'), row('c3', 'VALIDATED'), row('c4')]) {
    facade.setPeriod({
      periodType: 'ROLLING_24H',
      periodStart: '2026-10-30T14:00:00-05:00',
      periodEnd: '2026-10-31T14:00:00-05:00',
    });
    const loading = facade.load();
    http.expectOne((request) => request.url === GRID_URL).flush(rows);
    await loading;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CaptureFacade],
    });
    facade = TestBed.inject(CaptureFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide la grilla con el período exacto', async () => {
    facade.setPeriod({
      periodType: 'ROLLING_7D',
      periodStart: '2026-10-24T00:00:00-05:00',
      periodEnd: '2026-10-31T00:00:00-05:00',
    });
    const loading = facade.load();
    const pending = http.expectOne((candidate) => candidate.url === GRID_URL);
    expect(pending.request.params.get('periodStart')).toBe('2026-10-24T00:00:00-05:00');
    expect(pending.request.params.get('periodEnd')).toBe('2026-10-31T00:00:00-05:00');
    pending.flush([]);
    await loading;
  });

  it('reinicia lo digitado al cambiar de conector', async () => {
    await loadGrid();
    facade.select('c1');
    typeDraft();

    facade.select('c2');

    expect(facade.current()?.connectorId).toBe('c2');
    expect(facade.draft().available).toBe('');
  });

  it('«Guardar y siguiente» pasa al próximo conector sin medición y sin arrastrar valores', async () => {
    await loadGrid();
    facade.select('c2');
    typeDraft();

    const saving = facade.save(true);
    http
      .expectOne(MEASUREMENTS_URL)
      .flush({ id: 'm1', version: 0, result: 'CREATED', observations: [], reviewState: 'PENDING' });
    await saving;

    expect(facade.current()?.connectorId).toBe('c4');
    expect(facade.draft().available).toBe('');
    expect(facade.rows().find((item) => item.connectorId === 'c2')?.situation).toBe('PENDING');
  });

  it('avisa la observación sin perder el avance', async () => {
    await loadGrid();
    facade.select('c1');
    typeDraft();

    const saving = facade.save(false);
    http.expectOne(MEASUREMENTS_URL).flush({
      id: 'm1',
      version: 0,
      result: 'CREATED',
      observations: ['SUM_BELOW_DURATION'],
      reviewState: 'PENDING',
    });
    await saving;

    expect(facade.notice()).toContain('menor que el tiempo evaluado');
    expect(facade.rows()[0].situation).toBe('OBSERVED');
    expect(facade.current()?.connectorId).toBe('c1');
  });

  it('conserva lo digitado ante un error', async () => {
    await loadGrid();
    facade.select('c1');
    typeDraft();

    const saving = facade.save(true);
    http
      .expectOne(MEASUREMENTS_URL)
      .flush(
        { code: 'UPTIME_PERIOD_FUTURE', detail: 'El fin del periodo no puede estar en el futuro.' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await saving;

    expect(facade.error()).toContain('futuro');
    expect(facade.current()?.connectorId).toBe('c1');
    expect(facade.draft().available).toBe('1296');
  });

  describe('medicion distinta ya registrada', () => {
    async function conflict() {
      await loadGrid();
      facade.select('c1');
      typeDraft();
      const saving = facade.save(false);
      http
        .expectOne(MEASUREMENTS_URL)
        .flush(
          { code: 'UPTIME_MEASUREMENT_DIFFERS', detail: 'Ya existe.', id: 'm1', version: 4 },
          { status: 409, statusText: 'Conflict' },
        );
      await vi.waitFor(() =>
        http.expectOne((request) => request.url === GRID_URL).flush([row('c1', 'PENDING')]),
      );
      await saving;
    }

    it('recarga, conserva lo digitado y ofrece corregir', async () => {
      await conflict();

      expect(facade.conflict()).toEqual({ id: 'm1', version: 4 });
      expect(facade.draft().available).toBe('1296');
      expect(facade.current()?.situation).toBe('PENDING');
    });

    it('la correccion usa la version recibida y la justificacion', async () => {
      await conflict();

      const saving = facade.saveCorrection('Dato corregido');
      const request = http.expectOne(`${MEASUREMENTS_URL}/m1/corrections`);
      expect(request.request.body).toMatchObject({
        version: 4,
        availableMinutes: 1296,
        justification: 'Dato corregido',
      });
      request.flush(saved());

      expect(await saving).toBe(true);
      expect(facade.conflict()).toBeNull();
    });

    it('una version obsoleta se refresca sin perder lo digitado', async () => {
      await conflict();

      const saving = facade.saveCorrection('Dato corregido');
      http
        .expectOne(`${MEASUREMENTS_URL}/m1/corrections`)
        .flush(
          { code: 'CONCURRENT_MODIFICATION', detail: 'Otra persona la modifico.' },
          { status: 409, statusText: 'Conflict' },
        );
      await vi.waitFor(() =>
        http.expectOne(`${MEASUREMENTS_URL}/m1`).flush({ id: 'm1', version: 7 }),
      );

      expect(await saving).toBe(false);
      expect(facade.conflict()).toEqual({ id: 'm1', version: 7 });
      expect(facade.draft().offline).toBe('39');
      expect(facade.error()).toContain('modifico');
    });
  });

  it('interpreta el bloque, rellena el borrador y lo guarda con origen BLOCK', async () => {
    await loadGrid();
    facade.select('c1');

    const parsing = facade.parseBlock('Disponible 1296');
    http
      .expectOne(`${MEASUREMENTS_URL}/parse`)
      .flush({ available: 1296, occupied: 105, fault: 0, offline: 39, reportedPercent: '97.29' });
    expect(await parsing).toBe(true);
    expect(facade.draft().available).toBe('1296');
    expect(facade.draft().reportedPercent).toBe('97.29');

    const saving = facade.save(false);
    const request = http.expectOne(MEASUREMENTS_URL);
    expect(request.request.body.origin).toBe('BLOCK');
    expect(request.request.body.sourceText).toBe('Disponible 1296');
    request.flush({ id: 'm1', version: 0, observations: [] });
    await saving;
  });

  it('un bloque ambiguo no toca lo digitado y nombra lo que falta', async () => {
    await loadGrid();
    facade.select('c1');
    typeDraft();

    const parsing = facade.parseBlock('Disponible');
    http.expectOne(`${MEASUREMENTS_URL}/parse`).flush(
      {
        detail: 'El bloque no se pudo interpretar.',
        code: 'UPTIME_BLOCK_AMBIGUOUS',
        missing: ['Ocupado', 'Con falla'],
      },
      { status: 422, statusText: 'Unprocessable' },
    );

    expect(await parsing).toBe(false);
    expect(facade.draft().available).toBe('1296');
    expect(facade.error()).toContain('Ocupado, Con falla');
  });

  it('cambiar de conector olvida el bloque interpretado', async () => {
    await loadGrid();
    facade.select('c1');
    const parsing = facade.parseBlock('Disponible 1296');
    http
      .expectOne(`${MEASUREMENTS_URL}/parse`)
      .flush({ available: 1296, occupied: 105, fault: 0, offline: 39 });
    await parsing;

    facade.select('c2');
    typeDraft();
    const saving = facade.save(false);
    const request = http.expectOne(MEASUREMENTS_URL);
    expect(request.request.body.origin).toBe('MANUAL');
    expect(request.request.body.sourceText).toBeUndefined();
    request.flush({ id: 'm1', version: 0, observations: [] });
    await saving;
  });
});

const saved = () => ({ id: 'm1', version: 5, observations: [] });
