import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { TicketPageResponse, TicketSummaryResponse } from '../../../../core/api/generated';
import { FileSaver } from '../../../../core/files/file-saver';
import { Notifier } from '../../../../core/notifications/notifier';
import { ControlFacade } from '../../application/control-facade';
import { ControlPage } from './control-page';

const URL = '/api/v1/incidents/tickets';

function summary(
  code: string,
  overrides: Partial<TicketSummaryResponse> = {},
): TicketSummaryResponse {
  return {
    id: `id-${code}`,
    code,
    state: 'OPEN',
    elementClass: 'EQUIPMENT',
    elementLabel: 'Electrolinera X / Conectores 1 y 2',
    incidentType: 'DISCONNECTION',
    incidentTypeLabel: 'Desconexión',
    priority: 'HIGH',
    responsible: { id: 'u1', name: 'Ana' },
    t0: '2026-11-21T15:00:00Z',
    lastActivityAt: '2026-11-21T15:00:00Z',
    episodeCount: 1,
    version: 0,
    ...overrides,
  };
}

function page(items: TicketSummaryResponse[], total = items.length): TicketPageResponse {
  return { items, total, page: 0, size: 20, cutOff: '2026-11-22T20:30:00Z' };
}

describe('ControlPage', () => {
  let http: HttpTestingController;
  const saver = { download: vi.fn().mockResolvedValue(undefined) };
  const notifier = { error: vi.fn(), success: vi.fn() };

  function setup(): ComponentFixture<ControlPage> {
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
    const fixture = TestBed.createComponent(ControlPage);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  async function load(
    fixture: ComponentFixture<ControlPage>,
    items: TicketSummaryResponse[],
    open = 1,
  ) {
    TestBed.tick();
    const requests = http.match((r) => r.url === URL);
    requests.find((r) => r.request.params.get('size') === '20')?.flush(page(items));
    requests.find((r) => r.request.params.get('size') === '1')?.flush(page([], open));
    const facade = fixture.debugElement.injector.get(ControlFacade);
    await vi.waitFor(() => expect(facade.loading()).toBe(false));
    fixture.detectChanges();
  }

  const text = (fixture: ComponentFixture<ControlPage>) =>
    (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

  it('muestra la tabla de tres columnas con el elemento actual, el estado y el pie de totales', async () => {
    const fixture = setup();
    await load(
      fixture,
      [
        summary('TICKET-9001'),
        summary('TICKET-9002', { state: 'CLOSED', elementLabel: 'Servicio de chatbot' }),
      ],
      1,
    );

    const headers = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('th')).map(
      (th) => th.textContent?.trim(),
    );
    expect(headers).toEqual(['Número de ticket', 'Cargador o electrolinera', 'Estado del ticket']);
    const content = text(fixture);
    expect(content).toContain('TICKET-9001');
    expect(content).toContain('Servicio de chatbot');
    expect(content).toContain('Abierto');
    expect(content).toContain('Cerrado');
    expect(content).toContain('Total: 2 · Abiertos: 1 · Cerrados: 1');
  });

  it('abre en «Todo el historial» y lo ofrece primero, con el corte y la zona', async () => {
    const fixture = setup();
    await load(fixture, [summary('TICKET-9001')]);

    const chips = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('mat-chip-option'),
    ).map((chip) => chip.textContent?.trim());
    expect(chips[0]).toBe('Todo el historial');
    expect(text(fixture)).toContain('Corte:');
    expect(text(fixture)).toContain('America/Guayaquil');
  });

  it('el estado de la fila es el del final del periodo', async () => {
    const fixture = setup();
    await load(fixture, [summary('TICKET-9001', { state: 'CLOSED', stateAtPeriodEnd: 'OPEN' })]);

    const badge = (fixture.nativeElement as HTMLElement).querySelector(
      'tbody sarc-ticket-state-badge',
    );
    expect(badge?.textContent?.trim()).toBe('Abierto');
  });

  it('sin tickets dice «Sin tickets en el período» y no muestra el pie', async () => {
    const fixture = setup();
    await load(fixture, [], 0);

    expect(text(fixture)).toContain('Sin tickets en el período');
    expect(text(fixture)).not.toContain('Total:');
  });

  it('los botones de descarga son secundarios, van a la derecha del encabezado y piden el formato', async () => {
    const fixture = setup();
    await load(fixture, [summary('TICKET-9001')]);

    const buttons = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
        '.header-actions button',
      ),
    );
    expect(buttons.map((button) => button.textContent?.trim())).toEqual([
      'Descargar PDF',
      'Descargar Excel',
    ]);
    buttons[1].click();
    await vi.waitFor(() => expect(saver.download).toHaveBeenCalledOnce());

    expect(saver.download.mock.calls[0][1]).toBe('control-tickets.xlsx');
  });

  it('si la descarga falla avisa con el mensaje del servidor', async () => {
    const fixture = setup();
    await load(fixture, [summary('TICKET-9001')]);
    saver.download.mockRejectedValueOnce({ detail: 'No se pudo generar el informe.' });

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.header-actions button')
      ?.click();

    await vi.waitFor(() =>
      expect(notifier.error).toHaveBeenCalledWith('No se pudo generar el informe.'),
    );
  });
});
