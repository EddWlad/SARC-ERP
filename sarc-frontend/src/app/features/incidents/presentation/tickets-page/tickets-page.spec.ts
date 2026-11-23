import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TicketPageResponse, TicketSummaryResponse } from '../../../../core/api/generated';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { SessionStore } from '../../../../core/auth/session-store';
import { MessageDialog } from '../message-dialog/message-dialog';
import { TicketCreateDialog } from '../ticket-create-dialog/ticket-create-dialog';
import { TicketsPage } from './tickets-page';

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
    elementLabel: 'KIA Norte / Conector 2',
    network: 'KIA',
    incidentType: 'DISCONNECTION',
    incidentTypeLabel: 'Desconexión',
    priority: 'HIGH',
    responsible: { id: 'u1', name: 'Ana Analista' },
    t0: '2026-11-21T15:00:00Z',
    lastActivityAt: '2026-11-21T20:30:00Z',
    episodeCount: 1,
    version: 0,
    ...overrides,
  };
}

function page(
  items: TicketSummaryResponse[],
  extra: Partial<TicketPageResponse> = {},
): TicketPageResponse {
  return {
    items,
    total: items.length,
    page: 0,
    size: 20,
    cutOff: '2026-11-21T21:00:00Z',
    ...extra,
  };
}

describe('TicketsPage', () => {
  let http: HttpTestingController;

  function setup(roles: boolean): ComponentFixture<TicketsPage> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SessionStore, useValue: { hasAnyRole: () => roles } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TicketsPage);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  async function load(fixture: ComponentFixture<TicketsPage>, response: TicketPageResponse) {
    http.expectOne((r) => r.url === URL).flush(response);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const text = (fixture: ComponentFixture<TicketsPage>) =>
    (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

  it('lista los tickets con su codigo, elemento actual, tipo, estado y responsable', async () => {
    const fixture = setup(true);
    await load(
      fixture,
      page([summary('TICKET-9001'), summary('TICKET-9002', { state: 'CLOSED' })]),
    );
    const content = text(fixture);
    expect(content).toContain('TICKET-9001');
    expect(content).toContain('KIA Norte / Conector 2');
    expect(content).toContain('Desconexión');
    expect(content).toContain('Alta');
    expect(content).toContain('Ana Analista');
    expect(content).toContain('Abierto');
    expect(content).toContain('Cerrado');
  });

  it('un editor ve «Nuevo ticket» a la derecha del encabezado; un rol de consulta no', async () => {
    const editor = setup(true);
    await load(editor, page([]));
    expect(editor.nativeElement.querySelector('.header-actions button')?.textContent).toContain(
      'Nuevo ticket',
    );

    const reader = setup(false);
    await load(reader, page([]));
    expect(reader.nativeElement.querySelector('.header-actions')).toBeNull();
  });

  it('«Nuevo ticket» abre el registro en un diálogo y no cambia de pestaña', async () => {
    const fixture = setup(true);
    await load(fixture, page([]));
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as never);

    (
      (fixture.nativeElement as HTMLElement).querySelector('.header-actions button') as HTMLElement
    ).click();
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(TicketCreateDialog, expect.anything());
  });

  it('sin tickets muestra el estado vacio', async () => {
    const fixture = setup(true);
    await load(fixture, page([]));
    expect(text(fixture)).toContain('No hay tickets que coincidan con los filtros');
  });

  it('un codigo anterior al numero inicial avisa que es de la secuencia anterior', async () => {
    const fixture = setup(true);
    await load(fixture, page([], { notice: 'TICKET_BEFORE_START' }));
    expect(text(fixture)).toContain(
      'Ese ticket pertenece a la secuencia externa anterior y no está registrado en SARC',
    );
  });

  it('si la carga falla muestra el error con «Reintentar»', async () => {
    const fixture = setup(true);
    http
      .expectOne((r) => r.url === URL)
      .flush({ code: 'X', detail: 'No se pudo cargar' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudo cargar',
    );
    expect(text(fixture)).toContain('Reintentar');
  });

  it('muestra la hora de la ultima actualizacion', async () => {
    const fixture = setup(true);
    await load(fixture, page([summary('TICKET-9001')]));
    expect(text(fixture)).toContain('Actualizado a las');
  });

  it('ordenar por una columna consulta con ese campo, y el elemento afectado no se ordena', async () => {
    const fixture = setup(true);
    await load(fixture, page([summary('TICKET-9001')]));
    const headers = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('th'),
    ) as HTMLElement[];
    const sortable = headers.filter((header) => header.querySelector('button.sort'));
    const elementHeader = headers.find((header) => header.textContent?.trim() === 'Elemento');
    expect(elementHeader?.querySelector('button')).toBeNull();
    expect(sortable.length).toBe(8);

    (sortable[0].querySelector('button') as HTMLButtonElement).click();
    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('sort')).toBe('code,asc');
    request.flush(page([summary('TICKET-9001')]));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(sortable[0].getAttribute('aria-sort')).toBe('ascending');
  });

  describe('mensaje para WhatsApp', () => {
    it('cada fila tiene un menu con «Mensaje para WhatsApp», tambien para los roles de consulta', async () => {
      for (const editor of [true, false]) {
        const fixture = setup(editor);
        await load(fixture, page([summary('TICKET-9001')]));
        const trigger = (fixture.nativeElement as HTMLElement).querySelector(
          'button[aria-label="Acciones de TICKET-9001"]',
        ) as HTMLButtonElement;
        expect(trigger).not.toBeNull();
        trigger.click();
        fixture.detectChanges();
        await fixture.whenStable();
        const items = Array.from(document.querySelectorAll('[role="menuitem"]')).map((i) =>
          i.textContent?.trim(),
        );
        expect(items).toEqual(['Mensaje para WhatsApp']);
      }
    });

    it('abrir el mensaje pide el texto del ticket sin opciones y no ofrece registrar el envio', async () => {
      const fixture = setup(true);
      await load(fixture, page([summary('TICKET-9001')]));
      const dialog = TestBed.inject(MatDialog);
      const open = vi
        .spyOn(dialog, 'open')
        .mockReturnValue({ afterClosed: () => of(undefined) } as never);

      (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('button[aria-label="Acciones de TICKET-9001"]')
        ?.click();
      fixture.detectChanges();
      await fixture.whenStable();
      (document.querySelector('[role="menuitem"]') as HTMLButtonElement).click();
      await fixture.whenStable();

      expect(open).toHaveBeenCalledWith(MessageDialog, expect.anything());
      const data = open.mock.calls[0][1]?.data as {
        load: (o: { includeDetection: boolean; includeActions: boolean }) => Promise<unknown>;
        record?: unknown;
      };
      expect(data.record).toBeUndefined();
      const loading = data.load({ includeDetection: false, includeActions: false });
      const request = http.expectOne((r) => r.url === `${URL}/id-TICKET-9001/message`);
      expect(request.request.params.get('includeDetection')).toBe('false');
      expect(request.request.params.get('includeActions')).toBe('false');
      request.flush({ text: 'x', missingFields: [], ticketCode: 'TICKET-9001', state: 'OPEN' });
      await loading;
    });
  });
});
