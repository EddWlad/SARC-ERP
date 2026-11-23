import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { TicketPageResponse } from '../../../../core/api/generated';
import { FileSaver } from '../../../../core/files/file-saver';
import { Notifier } from '../../../../core/notifications/notifier';
import { DashboardFacade } from '../../application/dashboard-facade';
import { summaryResponse } from '../../testing/summary-fixture';
import { DashboardPage } from './dashboard-page';

const SUMMARY_URL = '/api/v1/incidents/summary';
const TICKETS_URL = '/api/v1/incidents/tickets';

function page(total: number): TicketPageResponse {
  return { items: [], total, page: 0, size: 1, cutOff: '2026-11-22T20:30:00Z' };
}

// El tablero monta Chart.js: con toda la suite en paralelo la primera prueba tarda mas de los 5 s por defecto.
vi.setConfig({ testTimeout: 20000 });

describe('DashboardPage', () => {
  let http: HttpTestingController;
  const saver = { download: vi.fn().mockResolvedValue(undefined) };
  const notifier = { error: vi.fn(), success: vi.fn() };

  function setup(): ComponentFixture<DashboardPage> {
    saver.download.mockClear();
    notifier.error.mockClear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: FileSaver, useValue: saver },
        { provide: Notifier, useValue: notifier },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  async function load(fixture: ComponentFixture<DashboardPage>, summary = summaryResponse()) {
    TestBed.tick();
    http.expectOne((r) => r.url === SUMMARY_URL).flush(summary);
    http.match((r) => r.url === TICKETS_URL).forEach((request) => request.flush(page(2)));
    const facade = fixture.debugElement.injector.get(DashboardFacade);
    await vi.waitFor(() => expect(facade.loading()).toBe(false));
    fixture.detectChanges();
  }

  const text = (fixture: ComponentFixture<DashboardPage>) =>
    (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

  it('muestra el resumen, las condiciones de los abiertos y los cerrados aparte', async () => {
    const fixture = setup();
    await load(fixture);

    const content = text(fixture);
    expect(content).toContain('Tickets únicos');
    expect(content).toContain('Abiertos al corte');
    expect(content).toContain('Cerrados por duplicado o error, aparte y fuera de los promedios: 1');
    expect(content).toContain('Pendientes de validación (sin T1)');
    expect(content).toContain('En atención (con T2 y sin T3)');
    expect(content).toContain('Recuperados en observación (con T3)');
  });

  it('cada promedio lleva su denominador y un periodo sin datos no se muestra como cero', async () => {
    const fixture = setup();
    await load(
      fixture,
      summaryResponse({
        measures: [
          { pair: 'T1-T0', averageMinutes: 4.3, n: 3, notApplicable: 0, noData: 1, open: 0 },
          { pair: 'T3-T0', n: 0, notApplicable: 0, noData: 0, open: 1 },
        ],
      }),
    );

    const rows = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'),
    ).map((row) => row.textContent?.replace(/\s+/g, ' ').trim());
    expect(rows[0]).toContain('4.3');
    expect(rows[0]).toContain('3');
    expect(rows[1]).toContain('—');
    expect(text(fixture)).toContain('Validaciones dentro de la referencia: 66.7 % (n = 3)');
  });

  it('las referencias llevan la leyenda de no SLA, la version y la justificacion de la desviacion', async () => {
    const fixture = setup();
    await load(fixture);

    const content = text(fixture);
    expect(content).toContain('Referencia operativa, no SLA contractual');
    expect(content).toContain('Versión de referencias: V1');
    expect(content).toContain('Falla del proveedor');
    expect(content).toContain('Sin referencia aprobada');
  });

  it('no calcula disponibilidad: remite a Uptime e Intermitencias', async () => {
    const fixture = setup();
    await load(fixture);

    const links = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>('a'),
    ).map((link) => link.getAttribute('href'));
    expect(links).toContain('/uptime');
    expect(links).toContain('/intermitencias');
    expect(text(fixture)).toContain('Este tablero no calcula disponibilidad');
  });

  it('sin episodios dice «Sin datos en el período» y omite los gráficos', async () => {
    const fixture = setup();
    await load(
      fixture,
      summaryResponse({
        totals: {
          uniqueTickets: 0,
          episodes: 0,
          openEpisodes: 0,
          closedEpisodes: 0,
          reopened: 0,
          critical: 0,
          excluded: 0,
        },
        byDay: [],
        byType: [],
        byPriority: [],
        repeaters: [],
      }),
    );

    expect(text(fixture)).toContain('Sin datos en el período');
    expect((fixture.nativeElement as HTMLElement).querySelector('sarc-chart-view')).toBeNull();
  });

  it('los botones de descarga son secundarios y a la derecha del encabezado', async () => {
    const fixture = setup();
    await load(fixture);

    const buttons = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
        '.header-actions button',
      ),
    );
    expect(buttons.map((button) => button.textContent?.trim())).toEqual([
      'Descargar PDF',
      'Descargar Excel',
    ]);
    buttons[0].click();
    await vi.waitFor(() => expect(saver.download).toHaveBeenCalledOnce());
    expect(saver.download.mock.calls[0][1]).toBe('gestion-incidencias.pdf');
  });

  it('si la descarga falla avisa con el mensaje del servidor', async () => {
    const fixture = setup();
    await load(fixture);
    saver.download.mockRejectedValueOnce({ detail: 'El período admite hasta 366 días' });

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.header-actions button')
      ?.click();

    await vi.waitFor(() =>
      expect(notifier.error).toHaveBeenCalledWith('El período admite hasta 366 días'),
    );
  });
});
