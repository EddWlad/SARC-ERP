import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CaptureFacade } from '../../application/capture-facade';
import { OpenWindowsPanel } from '../open-windows-panel/open-windows-panel';
import { PointPicker } from '../point-picker/point-picker';
import { CapturePage } from './capture-page';

describe('CapturePage (intermitencias)', () => {
  function render() {
    TestBed.configureTestingModule({
      imports: [CapturePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.overrideComponent(CapturePage, {
      remove: { imports: [OpenWindowsPanel, PointPicker] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(CapturePage);
    fixture.detectChanges();
    const facade = fixture.debugElement.injector.get(CaptureFacade);
    const root = fixture.nativeElement as HTMLElement;
    return { fixture, facade, root };
  }

  const buttons = (root: HTMLElement) =>
    Array.from(root.querySelectorAll('button'), (button) => button as HTMLButtonElement);
  const named = (root: HTMLElement, text: string) =>
    buttons(root).find((button) => button.textContent?.includes(text));

  it('muestra el titulo, el dia de hoy y una ventana vacia', () => {
    const { root, facade } = render();

    expect(root.querySelector('h1')?.textContent).toContain('Registrar intermitencias');
    expect(facade.date()).not.toBe('');
    expect(root.querySelectorAll('.window')).toHaveLength(1);
    expect(root.textContent).toContain('Inicio 1');
  });

  it('no deja guardar hasta tener punto y una ventana valida', () => {
    const { root, facade, fixture } = render();
    expect(named(root, 'Guardar')?.disabled).toBe(true);

    facade.selectPoint({
      id: 'p1',
      name: 'Punto A',
      assignedCode: '900001',
      adminStatus: 'ACTIVE',
    });
    facade.updateRow(facade.rows()[0].key, { start: '10:00', end: '10:30' });
    fixture.detectChanges();

    expect(named(root, 'Guardar')?.disabled).toBe(false);
    expect(named(root, 'Guardar y registrar otro punto')?.disabled).toBe(false);
  });

  it('agrega y quita ventanas sin limite', () => {
    const { root, fixture } = render();

    for (let n = 0; n < 6; n++) {
      named(root, 'Agregar ventana')!.click();
      fixture.detectChanges();
    }
    expect(root.querySelectorAll('.window')).toHaveLength(7);

    (root.querySelector('button[aria-label="Quitar la ventana 7"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root.querySelectorAll('.window')).toHaveLength(6);
  });

  it('muestra la duracion en h:mm y en minutos, y el total del dia', () => {
    const { root, facade, fixture } = render();
    facade.updateRow(facade.rows()[0].key, { start: '08:00', end: '10:02' });
    fixture.detectChanges();

    expect(root.querySelector('.summary .badge')?.textContent).toContain('2:02 · 122 min');
    expect(root.querySelector('.totals')?.textContent).toContain('2:02 · 122 min');
  });

  it('una ventana abierta no pide fin y se rotula como provisional', () => {
    const { root, facade, fixture } = render();
    facade.updateRow(facade.rows()[0].key, { start: '16:01', open: true });
    fixture.detectChanges();

    expect(root.querySelector('.summary')?.textContent).toContain('Abierta');
    expect(root.textContent).not.toContain('Fin 1');
    expect(root.querySelector('.issue')).toBeNull();
  });

  it('avisa que un fin anterior al inicio no cruza la medianoche solo', () => {
    const { root, facade, fixture } = render();
    facade.updateRow(facade.rows()[0].key, { start: '23:50', end: '00:20' });
    fixture.detectChanges();

    expect(root.querySelector('.issue')?.textContent).toContain('Si termina al día siguiente');
    expect(root.textContent).toContain('Termina el día siguiente');
  });

  it('muestra el motivo del servidor junto a su ventana', () => {
    const { root, facade, fixture } = render();
    const key = facade.rows()[0].key;
    facade.updateRow(key, { start: '10:00', end: '10:30' });
    facade.rowErrors.set({ [key]: 'La ventana se solapa con otra del mismo punto' });
    fixture.detectChanges();

    expect(root.querySelector('.window .issue')?.textContent).toContain('se solapa');
  });

  it('confirma lo guardado con su estado', async () => {
    const { root, facade, fixture } = render();
    const http = TestBed.inject(HttpTestingController);
    facade.selectPoint({
      id: 'p1',
      name: 'Punto A',
      assignedCode: '900001',
      adminStatus: 'ACTIVE',
    });
    facade.updateRow(facade.rows()[0].key, { start: '08:00', end: '13:00' });

    const saving = facade.save(false);
    http.expectOne('/api/v1/intermittency/windows').flush({
      equipmentId: 'p1',
      windows: [
        {
          id: 'w1',
          version: 0,
          equipmentId: 'p1',
          pointLabel: 'Punto A (900001)',
          startAt: '2026-10-15T13:00:00Z',
          endAt: '2026-10-15T18:00:00Z',
          durationMinutes: 300,
          durationText: '5:00',
          state: 'REVIEW',
          reviewReasons: ['LONG_WINDOW'],
          source: 'MANUAL',
          revision: 1,
        },
      ],
      dayTotals: [{ day: '2026-10-15', minutes: 300, text: '5:00', windows: 1 }],
    });
    await saving;
    fixture.detectChanges();

    const saved = root.querySelector('.saved');
    expect(saved?.textContent).toContain('Se guardaron 1 ventanas');
    expect(saved?.textContent).toContain('Por revisar');
    http.verify();
  });
});
