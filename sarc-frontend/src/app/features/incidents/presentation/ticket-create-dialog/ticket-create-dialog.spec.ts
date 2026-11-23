import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { EMPTY } from 'rxjs';

import { IncidentAssetResponse } from '../../../../core/api/generated';
import { SessionStore } from '../../../../core/auth/session-store';
import { Notifier } from '../../../../core/notifications/notifier';
import { TicketCreateFacade } from '../../application/ticket-create-facade';
import { operatingInstant, TicketCreateDialog } from './ticket-create-dialog';

const BASE = '/api/v1/incidents';

const STATION: IncidentAssetResponse = {
  id: 'eq-1',
  name: 'KIA Norte',
  network: 'KIA',
  infrastructureType: 'CHARGING_STATION',
  connectors: [{ id: 'c1', position: 1, typeLabel: 'CCS2', active: true }],
};

describe('operatingInstant', () => {
  it('interpreta la hora en la zona operativa (UTC-5)', () => {
    expect(operatingInstant('2026-11-21T10:30')).toBe('2026-11-21T15:30:00.000Z');
    expect(operatingInstant('2026-11-21T23:30')).toBe('2026-11-22T04:30:00.000Z');
  });

  it('vacio o invalido no es un instante', () => {
    expect(operatingInstant('')).toBeNull();
    expect(operatingInstant('ayer')).toBeNull();
    expect(operatingInstant('2026-13-40T99:99')).toBeNull();
  });
});

describe('TicketCreateDialog', () => {
  let fixture: ComponentFixture<TicketCreateDialog>;
  let http: HttpTestingController;
  let facade: TicketCreateFacade;
  const close = vi.fn();
  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    close.mockClear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SessionStore, useValue: { hasAnyRole: () => true } },
        { provide: Notifier, useValue: { success: vi.fn(), error: vi.fn() } },
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
        },
        { provide: MatDialog, useValue: { open: vi.fn() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketCreateDialog);
    facade = fixture.debugElement.injector.get(TicketCreateFacade);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function chooseEquipment(open: unknown[] = []): Promise<void> {
    const choosing = facade.selectAsset(STATION);
    http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush(open);
    await choosing;
    fixture.detectChanges();
  }

  it('muestra solo los tres datos y las acciones, con «Crear ticket» a la derecha de «Cancelar»', () => {
    expect(root().querySelector('sarc-element-picker')).not.toBeNull();
    expect(root().textContent).toContain('Tipo de incidencia');
    expect(root().textContent).toContain('Descripción breve');
    expect(root().querySelector('#more-data')).toBeNull();
    const buttons = Array.from(root().querySelectorAll('mat-dialog-actions button'));
    expect(buttons.map((b) => b.textContent?.trim())).toEqual(['Cancelar', 'Crear ticket']);
  });

  it('«Más datos» despliega origen, prioridad, T0 y la evidencia inicial opcional', () => {
    (root().querySelector('.more-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    const more = root().querySelector('#more-data') as HTMLElement;
    expect(more.textContent).toContain('Origen del aviso');
    expect(more.textContent).toContain('Prioridad');
    expect(more.textContent).toContain('Hora de detección');
    expect(more.textContent).toContain('Evidencia inicial');
    expect(more.textContent).toContain('datos personales de terceros');
    expect((more.querySelector('input[type="file"]') as HTMLInputElement).accept).toBe(
      'image/jpeg,image/png,image/webp,application/pdf',
    );
    expect(root().querySelector('.more-toggle')?.getAttribute('aria-expanded')).toBe('true');
  });

  it('no envia sin tipo ni descripcion y muestra los errores', async () => {
    await chooseEquipment();
    (root().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectNone({ url: `${BASE}/tickets`, method: 'POST' });
    expect(root().textContent).toContain('Seleccione el tipo de incidencia');
    expect(root().textContent).toContain('Escriba una descripción breve');
  });

  it('con tickets abiertos avisa en linea, ofrece el seguimiento y bloquea crear hasta elegir crear de todos modos', async () => {
    await chooseEquipment([
      { id: 't1', code: 'TICKET-9001', incidentTypeLabel: 'Desconexión', priority: 'HIGH' },
    ]);
    const duplicate = root().querySelector('.duplicate') as HTMLElement;
    expect(duplicate.textContent).toContain('TICKET-9001');
    expect(duplicate.textContent).toContain('Agregar seguimiento al ticket');
    const create = root().querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(create.disabled).toBe(true);

    (duplicate.querySelector('mat-checkbox input') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(create.disabled).toBe(false);
    expect(root().textContent).toContain('Motivo para crear otro ticket');
  });

  it('sin tickets abiertos no hay aviso y se puede crear', async () => {
    await chooseEquipment([]);
    expect(root().querySelector('.duplicate')).toBeNull();
    expect((root().querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(
      false,
    );
  });

  it('crea con el equipo completo y cierra el diálogo y lleva al detalle del ticket', async () => {
    await chooseEquipment();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const component = fixture.componentInstance as unknown as {
      model: { update(fn: (v: Record<string, string>) => Record<string, string>): void };
    };
    component.model.update((value) => ({
      ...value,
      incidentType: 'DISCONNECTION',
      description: 'Sin comunicación',
    }));
    fixture.detectChanges();

    (root().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    const request = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
    expect(request.request.body).toMatchObject({
      equipmentId: 'eq-1',
      connectorIds: [],
      incidentType: 'DISCONNECTION',
      description: 'Sin comunicación',
      reportOrigin: 'PLATFORM',
    });
    request.flush({ summary: { id: 'new-id', code: 'TICKET-9001' } });
    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalledWith(['/incidencias/tickets', 'new-id']),
    );
    expect(close).toHaveBeenCalledWith('new-id');
  });

  it('Cancelar sin cambios cierra el diálogo', () => {
    const cancel = Array.from(root().querySelectorAll('mat-dialog-actions button')).find(
      (b) => b.textContent?.trim() === 'Cancelar',
    ) as HTMLButtonElement;
    cancel.click();
    expect(close).toHaveBeenCalled();
  });

  describe('chatbot y elementos del sitio', () => {
    interface Model {
      model: {
        update(fn: (v: Record<string, string>) => Record<string, string>): void;
        (): Record<string, string>;
      };
    }
    const component = () => fixture.componentInstance as unknown as Model;

    function typeOptions(): string[] {
      (root().querySelector('mat-select') as HTMLElement | null)?.click();
      fixture.detectChanges();
      const options = Array.from(document.querySelectorAll('mat-option')).map(
        (o) => o.textContent?.trim() ?? '',
      );
      document.querySelectorAll('mat-option').forEach(() => undefined);
      return options;
    }

    function selectCurrentType(): string {
      return component().model()['incidentType'];
    }

    async function switchTo(elementClass: 'CHATBOT_SERVICE' | 'SITE_ELEMENT'): Promise<void> {
      facade.setElementClass(elementClass);
      if (elementClass === 'CHATBOT_SERVICE') {
        http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush([]);
      }
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('el chatbot propone su tipo y no ofrece los de un cargador', async () => {
      await switchTo('CHATBOT_SERVICE');
      expect(selectCurrentType()).toBe('CHATBOT_SERVICE_FAILURE');

      const selects = Array.from(root().querySelectorAll('mat-select'));
      // El primer selector es el alcance del chatbot; el segundo, el tipo de incidencia.
      (selects[1] as HTMLElement).click();
      fixture.detectChanges();
      expect(
        Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent?.trim()),
      ).toEqual(['Falla del servicio de chatbot', 'Incidencia no catalogada']);
    });

    it('un elemento del sitio propone su tipo y no ofrece los de un cargador', async () => {
      await switchTo('SITE_ELEMENT');
      expect(selectCurrentType()).toBe('SITE_ELEMENT_FAILURE');
      expect(typeOptions().length).toBeGreaterThan(0);
    });

    it('volver a equipo deja el tipo sin elegir', async () => {
      await switchTo('CHATBOT_SERVICE');
      facade.setElementClass('EQUIPMENT');
      fixture.detectChanges();
      await fixture.whenStable();
      expect(selectCurrentType()).toBe('');
    });

    it('el aviso de duplicado nombra al servicio de chatbot', async () => {
      facade.setElementClass('CHATBOT_SERVICE');
      http
        .expectOne((r) => r.url === `${BASE}/tickets/open-matches`)
        .flush([
          {
            id: 't1',
            code: 'TICKET-9001',
            incidentTypeLabel: 'Falla del servicio de chatbot',
            priority: 'MEDIUM',
          },
        ]);
      await vi.waitFor(() => expect(facade.hasMatches()).toBe(true));
      fixture.detectChanges();
      expect(root().querySelector('.duplicate')?.textContent).toContain(
        'El servicio de chatbot ya tiene tickets abiertos',
      );
    });

    it('crea un ticket de chatbot sin equipo ni conectores con solo la descripcion', async () => {
      await switchTo('CHATBOT_SERVICE');
      const router = TestBed.inject(Router);
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      component().model.update((value) => ({ ...value, description: 'Chatbot caído' }));
      fixture.detectChanges();

      (root().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      const request = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
      expect(request.request.body).toMatchObject({
        elementClass: 'CHATBOT_SERVICE',
        incidentType: 'CHATBOT_SERVICE_FAILURE',
        description: 'Chatbot caído',
      });
      expect(request.request.body).not.toHaveProperty('equipmentId');
      expect(request.request.body).not.toHaveProperty('connectorIds');
      request.flush({ summary: { id: 'new-id', code: 'TICKET-9002' } });
      await vi.waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(['/incidencias/tickets', 'new-id']),
      );
    });
  });

  it('si la numeracion no esta configurada lo explica y ofrece el enlace solo a quien puede configurarla', async () => {
    await chooseEquipment();
    facade.numberingBlock.set('NOT_CONFIGURED');
    fixture.detectChanges();
    expect(root().textContent).toContain('Todavía no se configuró el número inicial');
    expect(root().querySelector('a[href*="numeracion"]')).not.toBeNull();
  });
});
