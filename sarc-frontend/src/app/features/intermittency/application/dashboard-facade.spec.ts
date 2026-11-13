import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { KiaPointResponse } from '../../../core/api/generated';
import { FileSaver } from '../../../core/files/file-saver';
import { summary } from '../testing/summary-fixtures';
import { DashboardFacade } from './dashboard-facade';

const SUMMARY_URL = '/api/v1/intermittency/summary';

const POINT: KiaPointResponse = {
  id: 'p1',
  name: 'Punto A',
  assignedCode: '900001',
  adminStatus: 'ACTIVE',
};

describe('DashboardFacade', () => {
  let http: HttpTestingController;
  const saver = { download: vi.fn() };

  function create(now: string): DashboardFacade {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(now));
    TestBed.resetTestingModule();
    saver.download.mockReset().mockImplementation(async (request) => {
      await firstValueFrom(request);
    });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileSaver, useValue: saver },
        DashboardFacade,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(DashboardFacade);
  }

  const request = () => http.expectOne((candidate) => candidate.url === SUMMARY_URL);

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('abre en la quincena en curso: la segunda a partir del dia 16', () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();

    const open = request();
    expect(open.request.params.get('preset')).toBe('SECOND_HALF');
    expect(open.request.params.get('month')).toBe('2026-10');
    expect(facade.range()).toEqual({ from: '2026-10-16', to: '2026-11-01' });
    open.flush(summary());
  });

  it('abre en la primera quincena hasta el dia 15', () => {
    const facade = create('2026-10-15T20:00:00-05:00');
    TestBed.tick();

    request().flush(summary());
    expect(facade.preset()).toBe('FIRST_HALF');
    expect(facade.range()).toEqual({ from: '2026-10-01', to: '2026-10-16' });
  });

  it('los ultimos 15 dias van de hoy menos 14 a hoy mas 1 y no llevan mes', () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());

    facade.choosePreset('LAST_15_DAYS');
    TestBed.tick();

    const next = request();
    expect(next.request.params.get('preset')).toBe('LAST_15_DAYS');
    expect(next.request.params.has('month')).toBe(false);
    expect(facade.range()).toEqual({ from: '2026-10-06', to: '2026-10-21' });
    next.flush(summary());
  });

  it('un rango personalizado incompleto no consulta y dice que falta', () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());

    facade.choosePreset('CUSTOM');
    TestBed.tick();

    http.expectNone((candidate) => candidate.url === SUMMARY_URL);
    expect(facade.rangeError()).toBe('Elija el primer y el último día.');

    facade.chooseRange('2026-10-01', '2026-10-15');
    TestBed.tick();

    const next = request();
    expect(next.request.params.get('preset')).toBe('CUSTOM');
    // El ultimo dia elegido se incluye: la API recibe el siguiente como fin exclusivo.
    expect(next.request.params.get('from')).toBe('2026-10-01');
    expect(next.request.params.get('to')).toBe('2026-10-16');
    expect(facade.rangeError()).toBe('');
    next.flush(summary());
  });

  it('sin un rango consultable no muestra las cifras del periodo anterior', async () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    expect(facade.summary()).toBeDefined();

    facade.choosePreset('CUSTOM');
    TestBed.tick();

    expect(facade.summary()).toBeUndefined();

    facade.chooseRange('2026-10-01', '2026-10-15');
    TestBed.tick();
    // Con un rango valido vuelven las cifras retenidas mientras llegan las nuevas.
    expect(facade.summary()).toBeDefined();
    request().flush(summary());
  });

  it('rechaza un ultimo dia anterior al primero y un rango de mas de 366 dias', () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());
    facade.choosePreset('CUSTOM');

    facade.chooseRange('2026-10-10', '2026-10-01');
    expect(facade.rangeError()).toContain('anterior al primero');
    facade.chooseRange('2025-01-01', '2026-10-01');
    expect(facade.rangeError()).toContain('366');
    TestBed.tick();

    http.expectNone((candidate) => candidate.url === SUMMARY_URL);
  });

  it('un rango de 366 dias se consulta', () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());
    facade.choosePreset('CUSTOM');

    facade.chooseRange('2025-10-01', '2026-10-01');
    TestBed.tick();

    expect(facade.rangeError()).toBe('');
    request().flush(summary());
  });

  it('envia cada punto elegido y vuelve a todos al limpiar', () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());

    facade.choosePoints([POINT, { ...POINT, id: 'p2' }]);
    TestBed.tick();
    const filtered = request();
    expect(filtered.request.params.getAll('equipmentIds')).toEqual(['p1', 'p2']);
    filtered.flush(summary());
    expect(facade.filtered()).toBe(true);

    facade.clearFilters();
    TestBed.tick();
    const cleared = request();
    expect(cleared.request.params.has('equipmentIds')).toBe(false);
    cleared.flush(summary());
    expect(facade.filtered()).toBe(false);
  });

  describe('descargar el informe', () => {
    const reportRequest = () =>
      http.expectOne((candidate) => candidate.url === '/api/v1/intermittency/report');

    it('pide el formato con el mismo periodo y los mismos puntos del tablero', async () => {
      const facade = create('2026-10-20T15:00:00-05:00');
      TestBed.tick();
      request().flush(summary());
      facade.choosePoints([POINT, { ...POINT, id: 'p2' }]);
      TestBed.tick();
      request().flush(summary());

      const downloading = facade.downloadReport('PDF');
      const report = reportRequest();

      expect(report.request.params.get('format')).toBe('PDF');
      expect(report.request.params.get('preset')).toBe('SECOND_HALF');
      expect(report.request.params.get('month')).toBe('2026-10');
      expect(report.request.params.getAll('equipmentIds')).toEqual(['p1', 'p2']);
      expect(report.request.responseType).toBe('blob');
      expect(facade.downloading()).toBe('PDF');
      report.flush(new Blob(['pdf']));
      await downloading;

      expect(saver.download).toHaveBeenCalledWith(expect.anything(), 'intermitencias.pdf');
      expect(facade.downloading()).toBeUndefined();
    });

    it('un rango personalizado viaja con sus dias y el fin exclusivo, y Excel con su extension', async () => {
      const facade = create('2026-10-20T15:00:00-05:00');
      TestBed.tick();
      request().flush(summary());
      facade.choosePreset('CUSTOM');
      facade.chooseRange('2026-10-01', '2026-10-15');
      TestBed.tick();
      request().flush(summary());

      const downloading = facade.downloadReport('XLSX');
      const report = reportRequest();

      expect(report.request.params.get('from')).toBe('2026-10-01');
      expect(report.request.params.get('to')).toBe('2026-10-16');
      expect(report.request.params.get('format')).toBe('XLSX');
      report.flush(new Blob(['xlsx']));
      await downloading;
      expect(saver.download).toHaveBeenCalledWith(expect.anything(), 'intermitencias.xlsx');
    });

    it('si falla rechaza con el error del servidor y libera el boton', async () => {
      const facade = create('2026-10-20T15:00:00-05:00');
      TestBed.tick();
      request().flush(summary());
      saver.download.mockRejectedValue({ detail: 'No se pudo generar el informe.' });

      await expect(facade.downloadReport('PDF')).rejects.toEqual({
        detail: 'No se pudo generar el informe.',
      });

      expect(facade.downloading()).toBeUndefined();
    });

    it('sin un rango consultable no pide nada', async () => {
      const facade = create('2026-10-20T15:00:00-05:00');
      TestBed.tick();
      request().flush(summary());
      facade.choosePreset('CUSTOM');
      TestBed.tick();

      await facade.downloadReport('PDF');

      http.expectNone((candidate) => candidate.url === '/api/v1/intermittency/report');
      expect(saver.download).not.toHaveBeenCalled();
    });
  });

  it('con un error muestra el mensaje del servidor', async () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();

    request().flush(
      { code: 'PERIOD_TOO_LONG', detail: 'El rango personalizado admite hasta 366 dias' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();

    expect(facade.error()).toBe('El rango personalizado admite hasta 366 dias');
  });

  it('conserva las cifras anteriores mientras llegan las nuevas', async () => {
    const facade = create('2026-10-20T15:00:00-05:00');
    TestBed.tick();
    request().flush(summary());
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    expect(facade.summary()?.official.windows).toBe(5);

    facade.choosePreset('LAST_15_DAYS');
    TestBed.tick();

    expect(facade.summary()?.official.windows).toBe(5);
    expect(facade.loading()).toBe(true);
    request().flush(summary());
  });
});
