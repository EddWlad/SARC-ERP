import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { KiaPointResponse } from '../../../core/api/generated';
import { CaptureFacade } from './capture-facade';

const WINDOWS_URL = '/api/v1/intermittency/windows';

const POINT: KiaPointResponse = {
  id: 'p1',
  name: 'Punto A',
  assignedCode: '900001',
  usageStatus: 'BLOCKED',
  adminStatus: 'ACTIVE',
};

describe('CaptureFacade (intermitencias)', () => {
  let facade: CaptureFacade;
  let http: HttpTestingController;

  const keys = () => facade.rows().map((row) => row.key);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CaptureFacade],
    });
    facade = TestBed.inject(CaptureFacade);
    http = TestBed.inject(HttpTestingController);
    facade.selectPoint(POINT);
    facade.setDate('2026-10-15');
  });

  afterEach(() => http.verify());

  it('empieza con una fila en blanco que no cuenta ni bloquea', () => {
    expect(facade.rows()).toHaveLength(1);
    expect(facade.rowViews()[0].blank).toBe(true);
    expect(facade.canSave()).toBe(false);
  });

  it('calcula la duracion de cada fila al vuelo', () => {
    facade.updateRow(keys()[0], { start: '10:00', end: '10:45' });

    expect(facade.rowViews()[0].minutes).toBe(45);
    expect(facade.rowViews()[0].issue).toBe('');
    expect(facade.canSave()).toBe(true);
  });

  it('acepta mas de cinco ventanas del mismo dia', () => {
    for (let n = 0; n < 6; n++) facade.addRow();
    facade
      .rows()
      .forEach((row, index) =>
        facade.updateRow(row.key, { start: `0${index + 1}:00`, end: `0${index + 1}:10` }),
      );

    expect(facade.rows()).toHaveLength(7);
    expect(facade.canSave()).toBe(true);
    expect(facade.dayTotals()).toEqual([{ day: '2026-10-15', minutes: 70 }]);
  });

  it('un fin anterior al inicio no se corrige solo: avisa y ofrece marcar el dia siguiente', () => {
    const key = keys()[0];
    facade.updateRow(key, { start: '23:50', end: '00:20' });

    expect(facade.rowViews()[0].issue).toContain('día siguiente');
    expect(facade.canSave()).toBe(false);

    facade.updateRow(key, { endsNextDay: true });
    expect(facade.rowViews()[0].minutes).toBe(30);
    expect(facade.rowViews()[0].endDay).toBe('2026-10-16');
    expect(facade.dayTotals()).toEqual([
      { day: '2026-10-15', minutes: 10 },
      { day: '2026-10-16', minutes: 20 },
    ]);
  });

  it('un fin con fecha completa permite una caida de varios dias', () => {
    facade.updateRow(keys()[0], { start: '20:00', end: '06:00', endDate: '2026-10-18' });

    expect(facade.rowViews()[0].minutes).toBe(3 * 1440 - 14 * 60);
    expect(facade.rowViews()[0].warning).toContain('4 horas');
    expect(facade.canSave()).toBe(true);
  });

  it('una ventana abierta no pide fin ni suma al total del dia', () => {
    const key = keys()[0];
    facade.updateRow(key, { start: '16:01', open: true });

    expect(facade.rowViews()[0].issue).toBe('');
    expect(facade.rowViews()[0].minutes).toBeNull();
    expect(facade.dayTotals()).toEqual([]);
    expect(facade.canSave()).toBe(true);
  });

  it('pide el fin si la ventana no esta abierta', () => {
    facade.updateRow(keys()[0], { start: '10:00' });

    expect(facade.rowViews()[0].issue).toContain('hora de fin');
    expect(facade.canSave()).toBe(false);
  });

  it('quitar la ultima fila deja una en blanco', () => {
    facade.removeRow(keys()[0]);

    expect(facade.rows()).toHaveLength(1);
    expect(facade.rowViews()[0].blank).toBe(true);
  });

  it('guarda las ventanas con instantes completos y deja una fila nueva', async () => {
    const [first] = keys();
    facade.updateRow(first, {
      start: '23:50',
      end: '00:20',
      endsNextDay: true,
      comment: ' Corte ',
    });
    facade.addRow();
    facade.updateRow(keys()[1], { start: '16:01', open: true });

    const saving = facade.save(false);
    const request = http.expectOne(WINDOWS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      equipmentId: 'p1',
      windows: [
        {
          startAt: '2026-10-15T23:50:00-05:00',
          endAt: '2026-10-16T00:20:00-05:00',
          comment: 'Corte',
        },
        { startAt: '2026-10-15T16:01:00-05:00' },
      ],
    });
    request.flush({ equipmentId: 'p1', windows: [], dayTotals: [] });

    expect(await saving).toBe(true);
    expect(facade.rows()).toHaveLength(1);
    expect(facade.rowViews()[0].blank).toBe(true);
    expect(facade.point()).toEqual(POINT);
    expect(facade.date()).toBe('2026-10-15');
    expect(facade.savedCount()).toBe(1);
  });

  it('guardar y registrar otro libera el punto y conserva la fecha', async () => {
    facade.updateRow(keys()[0], { start: '10:00', end: '10:10' });

    const saving = facade.save(true);
    http.expectOne(WINDOWS_URL).flush({ equipmentId: 'p1', windows: [], dayTotals: [] });
    await saving;

    expect(facade.point()).toBeNull();
    expect(facade.date()).toBe('2026-10-15');
  });

  it('no envia las filas en blanco', async () => {
    facade.addRow();
    facade.updateRow(keys()[1], { start: '10:00', end: '10:10' });

    const saving = facade.save(false);
    const request = http.expectOne(WINDOWS_URL);
    expect(request.request.body.windows).toHaveLength(1);
    request.flush({ equipmentId: 'p1', windows: [], dayTotals: [] });
    await saving;
  });

  it('con un error conserva lo digitado y lleva cada motivo a su fila', async () => {
    facade.updateRow(keys()[0], { start: '08:00', end: '08:10' });
    facade.addRow();
    facade.updateRow(keys()[1], { start: '10:30', end: '10:40' });

    const saving = facade.save(false);
    http.expectOne(WINDOWS_URL).flush(
      {
        code: 'WINDOW_OVERLAP',
        detail: 'Ninguna ventana se guardo: 1 tienen errores',
        errors: [
          {
            index: 1,
            code: 'WINDOW_OVERLAP',
            message: 'La ventana se solapa con otra del mismo punto',
          },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(await saving).toBe(false);
    expect(facade.rows()).toHaveLength(2);
    expect(facade.rowErrors()).toEqual({
      [keys()[1]]: 'La ventana se solapa con otra del mismo punto',
    });
    expect(facade.error()).toContain('Ninguna ventana se guardo');

    facade.updateRow(keys()[1], { end: '10:35' });
    expect(facade.rowErrors()).toEqual({});
  });

  it('no guarda sin punto ni fecha', async () => {
    facade.selectPoint(null);
    facade.updateRow(keys()[0], { start: '10:00', end: '10:10' });

    expect(await facade.save(false)).toBe(false);
    http.expectNone(WINDOWS_URL);
  });
});
