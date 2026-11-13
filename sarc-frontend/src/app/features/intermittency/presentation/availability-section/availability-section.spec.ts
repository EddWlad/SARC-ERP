import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AvailabilityFacade } from '../../application/availability-facade';
import { Availability } from '../../domain/intermittency-availability';
import { AvailabilitySection } from './availability-section';

const DATA: Availability = {
  period: {
    preset: 'FIRST_HALF',
    from: '2026-10-01',
    to: '2026-10-16',
    cutOff: '2026-10-20T15:00:00-05:00',
    timeZone: 'America/Guayaquil',
  },
  aggregate: { percent: 99.5, evaluatedMinutes: 14400, offlineMinutes: 72 },
  coverage: { coveredDays: 1, daysInScope: 15 },
  blockedDays: [{ day: '2026-10-03', pendingRows: 2 }],
  points: [
    {
      equipmentId: 'a',
      code: '900001',
      name: 'Punto A',
      percent: 95,
      evaluatedMinutes: 1440,
      offlineMinutes: 72,
      coveredDays: 1,
    },
    {
      equipmentId: 'b',
      code: '900002',
      name: 'Punto B',
      percent: 100,
      evaluatedMinutes: 1440,
      offlineMinutes: 0,
      coveredDays: 1,
    },
    { equipmentId: 'c', name: 'Punto C', evaluatedMinutes: 0, offlineMinutes: 0, coveredDays: 0 },
  ],
  exclusions: [{ equipmentId: 'd', code: '900009', name: 'Punto D', reason: 'DISCONNECTED' }],
  labels: { estimated: true, test: true, origin: 'Registro manual de intermitencias (Kibana)' },
  limitations: ['No observa Ocupado ni Con falla.', 'La resolución es el minuto.'],
};

describe('AvailabilitySection', () => {
  const facade = {
    data: signal<Availability | undefined>(DATA),
    loading: signal(false),
    error: signal(''),
    reload: vi.fn(),
  };

  function render(data: Availability | undefined = DATA) {
    TestBed.resetTestingModule();
    facade.data.set(data);
    TestBed.configureTestingModule({ imports: [AvailabilitySection] });
    TestBed.overrideComponent(AvailabilitySection, {
      set: { providers: [{ provide: AvailabilityFacade, useValue: facade }] },
    });
    const fixture = TestBed.createComponent(AvailabilitySection);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (root: HTMLElement) => root.textContent?.replace(/\s+/g, ' ') ?? '';

  beforeEach(() => {
    facade.error.set('');
    facade.loading.set(false);
    facade.reload.mockReset();
  });

  it('es una seccion propia rotulada como estimada y de prueba, sin presentarse como uptime ni Enerlink', () => {
    const root = render();

    expect(root.querySelector('h2')?.textContent).toBe('Disponibilidad estimada (prueba)');
    expect(root.querySelector('section')?.getAttribute('aria-labelledby')).toBe(
      'availability-title',
    );
    expect(text(root)).toContain('No es uptime ni una medición de Enerlink');
    expect(text(root)).toContain('Origen: Registro manual de intermitencias (Kibana).');
  });

  it('muestra el KPI ponderado con su cobertura, el tiempo evaluado y el fuera de linea', () => {
    const root = render();
    const values = Array.from(root.querySelectorAll('.kpi-value'), (node) =>
      node.textContent?.replace(/\s+/g, ' ').trim(),
    );

    expect(values[0]).toMatch(/^99[.,]50 %$/);
    expect(values[1]).toBe('1 de 15 días');
    expect(values[2]).toContain('240:00');
    expect(values[3]).toContain('1:12');
    expect(text(root)).toContain('Disponibilidad estimada por intermitencias (prueba)');
  });

  it('lista los cargadores en el orden recibido y «Sin datos» nunca es 100 %', () => {
    const root = render();
    const rows = Array.from(
      root.querySelectorAll('.point'),
      (node) => node.textContent?.replace(/\s+/g, ' ').trim() ?? '',
    );

    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Punto A');
    expect(rows[0]).toMatch(/95[.,]00 %/);
    expect(rows[1]).toMatch(/100[.,]00 %/);
    expect(rows[2]).toContain('Punto C');
    expect(rows[2]).toContain('Sin datos');
    expect(rows[2]).not.toMatch(/100/);
    expect(rows[2]).toContain('0 de 15 días cubiertos');
  });

  it('muestra los dias sin cubrir, las exclusiones con su motivo y las limitaciones', () => {
    const root = render();

    expect(text(root)).toContain('Días sin cubrir por filas por vincular');
    expect(text(root)).toContain('03/10/2026');
    expect(text(root)).toContain('2 filas');
    expect(text(root)).toContain('Punto D');
    expect(text(root)).toContain('Desconectado: instalado sin servicio');
    expect(text(root)).toContain('No observa Ocupado ni Con falla.');
    expect(text(root)).toContain('La resolución es el minuto.');
  });

  it('sin ningun dia cubierto dice que no hay datos y no inventa una cifra', () => {
    const root = render({
      ...DATA,
      aggregate: { evaluatedMinutes: 0, offlineMinutes: 0 },
      coverage: { coveredDays: 0, daysInScope: 15 },
      blockedDays: [],
      points: DATA.points.map((point) => ({
        equipmentId: point.equipmentId,
        name: point.name,
        evaluatedMinutes: 0,
        offlineMinutes: 0,
        coveredDays: 0,
      })),
    });

    expect(text(root)).toContain('Sin datos: ningún día del período está marcado como revisado');
    expect(root.querySelector('.kpi-value')?.textContent?.trim()).toBe('Sin datos');
    expect(text(root)).not.toMatch(/100[.,]00 %/);
  });

  it('un error se muestra con un reintento', () => {
    facade.error.set('La disponibilidad estimada no está disponible para tus roles');
    const root = render(undefined);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'no está disponible para tus roles',
    );
    (root.querySelector('.state button') as HTMLButtonElement).click();
    expect(facade.reload).toHaveBeenCalledOnce();
  });
});
