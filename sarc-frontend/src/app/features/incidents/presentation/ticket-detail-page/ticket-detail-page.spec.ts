import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { TicketDetailResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { CloseDialog } from '../close-dialog/close-dialog';
import { MessageDialog } from '../message-dialog/message-dialog';
import { ReopenDialog } from '../reopen-dialog/reopen-dialog';
import { TicketDetailPage } from './ticket-detail-page';

const URL = '/api/v1/incidents/tickets/id-1';

function detail(overrides: Partial<TicketDetailResponse> = {}): TicketDetailResponse {
  return {
    summary: {
      id: 'id-1',
      code: 'TICKET-9001',
      state: 'OPEN',
      elementClass: 'EQUIPMENT',
      elementLabel: 'KIA Norte Renombrada / Conector 2',
      network: 'KIA',
      incidentType: 'DISCONNECTION',
      incidentTypeLabel: 'Desconexión',
      priority: 'HIGH',
      responsible: { id: 'u1', name: 'Ana Analista' },
      t0: '2026-11-21T15:00:00Z',
      lastActivityAt: '2026-11-21T15:00:00Z',
      condition: 'PENDING_VALIDATION',
      episodeCount: 1,
      version: 0,
    },
    description: 'Sin comunicación',
    reportOrigin: 'PLATFORM',
    equipment: {
      id: 'eq-1',
      labelAtT0: 'KIA Norte',
      currentName: 'KIA Norte Renombrada',
      active: true,
    },
    connectors: [{ id: 'c2', positionAtT0: 2, currentPosition: 1, active: true }],
    location: 'Vía 1',
    mapsUrl: 'https://maps.example.test/x',
    referenceVersion: {
      code: 'P1',
      label: 'Protocolo de monitoreo firmado (versión por confirmar)',
    },
    episodes: [
      {
        ordinal: 1,
        state: 'OPEN',
        milestones: [
          {
            kind: 'T0',
            status: 'RECORDED',
            occurredAt: '2026-11-21T15:00:00Z',
            retroactive: false,
            orderWarningAck: false,
            recordedAt: '2026-11-21T15:00:00Z',
          },
          {
            kind: 'T1',
            status: 'PENDING',
            retroactive: false,
            orderWarningAck: false,
            recordedAt: '2026-11-21T15:00:00Z',
          },
          {
            kind: 'T2',
            status: 'NOT_APPLICABLE',
            reason: 'Sin apoyo',
            retroactive: false,
            orderWarningAck: false,
            recordedAt: '2026-11-21T15:00:00Z',
          },
          {
            kind: 'T3',
            status: 'NO_DATA',
            reason: 'Hora desconocida',
            retroactive: false,
            orderWarningAck: false,
            recordedAt: '2026-11-21T15:00:00Z',
          },
          {
            kind: 'T4',
            status: 'PENDING',
            retroactive: false,
            orderWarningAck: false,
            recordedAt: '2026-11-21T15:00:00Z',
          },
        ],
        durations: [
          { pair: 'T1-T0', missing: ['T1'] },
          { pair: 'T2-T0', missing: ['T2'] },
          { pair: 'T3-T0', missing: ['T3'] },
          { pair: 'T4-T0', missing: ['T4'] },
          { pair: 'T4-T3', missing: ['T3', 'T4'] },
        ],
        references: [
          { measure: 'RECOVERY', limitMinutes: 300, extendable: true, result: 'NOT_EVALUABLE' },
        ],
        elapsedOpenMinutes: 135,
      },
    ],
    visits: [],
    evidence: [],
    communications: [],
    relations: [
      { ticketId: 'id-2', code: 'TICKET-9002', kind: 'COMMON_FAILURE', reason: 'Enlace del sitio' },
    ],
    timeline: [
      {
        id: 'e1',
        type: 'TICKET_CREATED',
        occurredAt: '2026-11-21T15:00:00Z',
        recordedAt: '2026-11-21T15:00:00Z',
        recordedByName: 'Ana Analista',
        retroactive: false,
        details: {},
        annulled: false,
      },
    ],
    allowedActions: [],
    ...overrides,
  };
}

describe('TicketDetailPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<TicketDetailPage>;
  const notifier = { success: vi.fn(), error: vi.fn() };

  async function open(response: TicketDetailResponse | null, status = 200) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Notifier, useValue: notifier },
      ],
    });
    notifier.success.mockClear();
    notifier.error.mockClear();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketDetailPage);
    fixture.componentRef.setInput('id', 'id-1');
    fixture.detectChanges();
    const request = http.expectOne(URL);
    if (response) {
      request.flush(response);
    } else {
      request.flush(
        { code: 'TICKET_NOT_FOUND', detail: 'El ticket no existe' },
        { status, statusText: 'x' },
      );
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

  /** El valor de un dato del detalle: el texto del `dd` que sigue al `dt` con esa etiqueta. */
  const fact = (label: string): string | undefined => {
    const term = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('dt')).find(
      (dt) => dt.textContent?.trim() === label,
    );
    return term?.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim();
  };

  afterEach(() => http.verify());

  it('muestra el codigo, el estado y el elemento con su nombre actual', async () => {
    await open(detail());
    expect(text()).toContain('TICKET-9001');
    expect(text()).toContain('Abierto');
    expect(text()).toContain('KIA Norte Renombrada / Conector 2');
  });

  it('conserva la etiqueta de T0 cuando el equipo cambio de nombre y la posicion de los conectores', async () => {
    await open(detail());
    expect(fact('Nombre en T0')).toBe('KIA Norte');
    expect(text()).toContain('Conector 1');
    expect(text()).toContain('(en T0: 2)');
    expect(fact('Red en T0')).toBe('KIA');
  });

  it('muestra la descripcion, el origen, la prioridad y la version de referencias como no SLA', async () => {
    await open(detail());
    expect(text()).toContain('Sin comunicación');
    expect(text()).toContain('Plataforma de monitoreo');
    expect(text()).toContain('Alta');
    expect(text()).toContain('Pendiente de validación');
    expect(text()).toContain('referencia operativa, no SLA contractual');
    expect(text()).toContain('Referencia operativa, no SLA contractual');
  });

  it('muestra los hitos con sus marcas y nunca una hora inventada para los pendientes', async () => {
    await open(detail());
    const rows = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.data-table tbody tr'),
    );
    const cells = rows.map((row) =>
      Array.from(row.querySelectorAll('th,td')).map((c) =>
        c.textContent?.replace(/\s+/g, ' ').trim(),
      ),
    );
    expect(cells[0][0]).toMatch(/^T0/);
    expect(cells[0][1]).toContain('R');
    expect(cells[0][2]).not.toBe('—');
    expect(cells[1][1]).toContain('Pendiente');
    expect(cells[1][2]).toBe('—');
    expect(cells[2][1]).toContain('N/A');
    expect(cells[2][3]).toBe('Sin apoyo');
    expect(cells[3][1]).toContain('S/D');
  });

  it('muestra las relaciones con su codigo y tipo, y la cronologia', async () => {
    await open(detail());
    expect(text()).toContain('TICKET-9002');
    expect(text()).toContain('Falla común');
    expect(text()).toContain('Enlace del sitio');
    expect(text()).toContain('Ticket creado');
    expect(text()).toContain('Ana Analista');
  });

  it('un ticket del chatbot no inventa equipo, red ni ubicacion', async () => {
    await open(
      detail({
        equipment: undefined,
        connectors: [],
        location: undefined,
        mapsUrl: undefined,
        summary: {
          ...detail().summary,
          elementClass: 'CHATBOT_SERVICE',
          elementLabel: 'Servicio de chatbot',
          network: undefined,
        },
      }),
    );
    expect(text()).toContain('Servicio de chatbot');
    expect(fact('Red en T0')).toBe('Sin red');
    expect(text()).not.toContain('Ver en Google Maps');
    expect(text()).not.toContain('Afectación');
  });

  it('un ticket inexistente lo dice y ofrece volver', async () => {
    await open(null, 404);
    expect(text()).toContain('El ticket no existe');
    expect(text()).toContain('Volver a los tickets');
  });

  describe('seguimiento (US2)', () => {
    const EDITOR_ACTIONS = [
      'MILESTONE',
      'EVENT',
      'CORRECT',
      'HANDOVER',
      'RESPONSIBLE',
      'VISIT',
      'EVIDENCE',
    ];

    const buttons = () =>
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).map(
        (button) => button.textContent?.replace(/\s+/g, ' ').trim() ?? '',
      );

    it('las duraciones sin hito se muestran como guion y nunca como cero', async () => {
      await open(detail());
      expect(fact('Validación (T1 − T0)')).toMatch(/^—/);
      expect(fact('Validación (T1 − T0)')).not.toContain('0:00');
      expect(fact('Recuperación (T3 − T0)')).toMatch(/^—/);
      expect(fact('Transcurrido desde T0')).toContain('2:15');
    });

    it('una duracion de cero minutos real se muestra como 0:00', async () => {
      const base = detail();
      await open(
        detail({
          episodes: [
            { ...base.episodes[0], durations: [{ pair: 'T1-T0', minutes: 0, missing: [] }] },
          ],
        }),
      );
      expect(fact('Validación (T1 − T0)')).toBe('0:00');
    });

    it('muestra la referencia con su leyenda y su resultado, y no la presenta como SLA', async () => {
      await open(detail());
      expect(text()).toContain('Recuperación · hasta 5:00 h (ampliable) · No evaluable aún');
      expect(text()).toContain('Referencia operativa, no SLA contractual');
    });

    it('un limite menor a una hora se muestra en minutos y no como 0:05 h', async () => {
      const base = detail();
      await open(
        detail({
          episodes: [
            {
              ...base.episodes[0],
              references: [
                { measure: 'VALIDATION', limitMinutes: 5, extendable: false, result: 'WITHIN' },
              ],
            },
          ],
        }),
      );
      expect(text()).toContain('Validación · hasta 5 min · Dentro de la referencia');
    });

    it('quien solo consulta no ve ninguna accion de escritura ni el recordatorio de carga', async () => {
      await open(detail({ allowedActions: [] }));
      const labels = buttons().join('|');
      expect(labels).not.toContain('Registrar ahora');
      expect(labels).not.toContain('Registrar evento');
      expect(labels).not.toContain('Relevo de turno');
      expect(labels).not.toContain('Solicitar visita');
      expect(labels).not.toContain('Adjuntar evidencia');
      expect(text()).not.toContain('datos personales de terceros');
    });

    it('un editor ve las acciones y el recordatorio de datos personales junto a la carga', async () => {
      await open(detail({ allowedActions: EDITOR_ACTIONS }));
      const labels = buttons();
      expect(labels).toContain('Registrar evento');
      expect(labels).toContain('Relevo de turno');
      expect(labels).toContain('Solicitar visita');
      expect(labels).toContain('Adjuntar evidencia');
      expect(text()).toContain('datos personales de terceros');
    });

    it('el menu «Más acciones» ofrece cambiar la prioridad y reclasificar solo si el servidor lo permite', async () => {
      const menuItems = async (actions: string[]): Promise<string[]> => {
        await open(detail({ allowedActions: actions }));
        const trigger = Array.from(
          (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
        ).find((b) => b.textContent?.includes('Más acciones')) as HTMLButtonElement | undefined;
        trigger?.click();
        fixture.detectChanges();
        await fixture.whenStable();
        return Array.from(document.querySelectorAll('[role="menuitem"]')).map(
          (item) => item.textContent?.trim() ?? '',
        );
      };

      expect(await menuItems(['PRIORITY', 'RECLASSIFY'])).toEqual([
        'Cambiar prioridad',
        'Reclasificar',
      ]);
      expect(await menuItems(['RECLASSIFY'])).toEqual(['Reclasificar']);
      expect(await menuItems(['EVENT'])).toEqual([]);
    });

    describe('cierre y reapertura', () => {
      const CLOSURE = {
        result: 'RECOVERED' as const,
        causeKind: 'CONFIRMED' as const,
        causeText: 'Falla del enlace',
        recommendations: 'Revisar el enlace',
        closureChecks: { platformNormal: 'CONFIRMED', postTest: 'NOT_APPLICABLE' },
        finalEvidenceWaiver: 'Sin captura',
      };

      function closedDetail(extra: Partial<TicketDetailResponse> = {}): TicketDetailResponse {
        const base = detail();
        const closed = {
          ...base.episodes[0],
          state: 'CLOSED' as const,
          milestones: base.episodes[0].milestones.map((m) =>
            m.kind === 'T4'
              ? { ...m, status: 'RECORDED' as const, occurredAt: '2026-11-21T16:00:00Z' }
              : m,
          ),
          elapsedOpenMinutes: undefined,
          closure: CLOSURE,
        };
        return {
          ...base,
          summary: { ...base.summary, state: 'CLOSED' },
          episodes: [closed],
          allowedActions: ['REOPEN', 'MESSAGE', 'RELATE', 'CORRECT_MILESTONES'],
          ...extra,
        };
      }

      const buttonTexts = () =>
        Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).map(
          (b) => b.textContent?.replace(/\s+/g, ' ').trim() ?? '',
        );

      async function openedDialog(button: string, response: TicketDetailResponse) {
        await open(response);
        const dialog = TestBed.inject(MatDialog);
        const spy = vi
          .spyOn(dialog, 'open')
          .mockReturnValue({ afterClosed: () => of(undefined) } as never);
        (
          Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
            b.textContent?.includes(button),
          ) as HTMLButtonElement
        ).click();
        await fixture.whenStable();
        return spy;
      }

      it('«Cerrar ticket» solo se ofrece a quien puede y en un ticket abierto', async () => {
        await open(detail({ allowedActions: [...EDITOR_ACTIONS, 'CLOSE'] }));
        expect(buttonTexts()).toContain('Cerrar ticket');
        expect(buttonTexts()).not.toContain('Reabrir ticket');
        await open(detail({ allowedActions: [] }));
        expect(buttonTexts()).not.toContain('Cerrar ticket');
      });

      it('«Cerrar ticket» abre el diálogo con la clase, el episodio abierto, las evidencias y las comunicaciones', async () => {
        const spy = await openedDialog(
          'Cerrar ticket',
          detail({ allowedActions: [...EDITOR_ACTIONS, 'CLOSE'] }),
        );
        expect(spy).toHaveBeenCalledWith(CloseDialog, expect.anything());
        const data = spy.mock.calls[0][1]?.data as Record<string, unknown>;
        expect(data['elementClass']).toBe('EQUIPMENT');
        expect((data['episode'] as { ordinal: number }).ordinal).toBe(1);
        expect(data['evidence']).toEqual([]);
        expect(data['communications']).toEqual([]);
        expect(data['save']).toBeTypeOf('function');
        expect(data['searchTickets']).toBeTypeOf('function');
      });

      it('un ticket cerrado ofrece «Reabrir ticket» y no las acciones de seguimiento', async () => {
        await open(closedDetail());
        expect(buttonTexts()).toContain('Reabrir ticket');
        expect(buttonTexts()).not.toContain('Registrar evento');
        expect(buttonTexts()).not.toContain('Cerrar ticket');
        expect(buttonTexts()).not.toContain('Registrar ahora');
      });

      it('«Reabrir ticket» abre el diálogo con el código del ticket', async () => {
        const spy = await openedDialog('Reabrir ticket', closedDetail());
        expect(spy).toHaveBeenCalledWith(ReopenDialog, expect.anything());
        expect((spy.mock.calls[0][1]?.data as { code: string }).code).toBe('TICKET-9001');
      });

      it('el resultado del cierre queda visible en el episodio cerrado', async () => {
        await open(closedDetail());
        expect(text()).toContain('Cierre');
        expect(text()).toContain('Recuperado');
        expect(text()).toContain('Confirmada · Falla del enlace');
        expect(text()).toContain('Revisar el enlace');
        expect(text()).toContain('Sin evidencia final');
        expect(text()).toContain('Sin captura');
        expect(fact('Prueba posterior realizada o no aplicable')).toBe('No aplica');
      });

      it('un duplicado muestra el código del original con un enlace', async () => {
        const base = closedDetail();
        await open({
          ...base,
          episodes: [
            {
              ...base.episodes[0],
              closure: {
                ...CLOSURE,
                result: 'DUPLICATE',
                duplicateOfTicketId: 'id-original',
                duplicateOfCode: 'TICKET-9002',
              },
            },
          ],
          allowedActions: [],
        });
        expect(text()).toContain('Duplicado de otro ticket de TICKET-9002');
        expect(
          (fixture.nativeElement as HTMLElement).querySelector('a[href$="/id-original"]'),
        ).not.toBeNull();
      });

      it('en un episodio cerrado se pueden corregir los hitos con justificación y en uno abierto no hay corrección de T4', async () => {
        await open(closedDetail());
        expect(buttonTexts().filter((label) => label === 'Corregir')).toHaveLength(5);
        await open(detail({ allowedActions: [...EDITOR_ACTIONS, 'CLOSE'] }));
        expect(buttonTexts().some((label) => label.includes('T4'))).toBe(false);
      });

      describe('con dos episodios', () => {
        function reopened(): TicketDetailResponse {
          const base = closedDetail();
          const second = {
            ...base.episodes[0],
            ordinal: 2,
            state: 'OPEN' as const,
            reopenReason: 'La falla volvió',
            closure: undefined,
            milestones: detail().episodes[0].milestones,
            elapsedOpenMinutes: 12,
          };
          return {
            ...base,
            summary: { ...base.summary, state: 'OPEN', episodeCount: 2 },
            episodes: [base.episodes[0], second],
            allowedActions: [...EDITOR_ACTIONS, 'CLOSE'],
          };
        }

        const toggles = () =>
          Array.from(
            (fixture.nativeElement as HTMLElement).querySelectorAll('mat-button-toggle'),
          ).map((t) => t.textContent?.replace(/\s+/g, ' ').trim());

        it('ofrece un selector y muestra por defecto el episodio actual', async () => {
          await open(reopened());
          expect(toggles()).toEqual(['Episodio 1 · cerrado', 'Episodio 2 · abierto']);
          expect(text()).toContain('Episodio 2');
          expect(text()).toContain('Reapertura: La falla volvió');
          expect(buttonTexts()).toContain('Registrar ahora');
        });

        it('un episodio anterior se ve en solo lectura, con su cierre', async () => {
          await open(reopened());
          (
            (fixture.nativeElement as HTMLElement).querySelectorAll(
              'mat-button-toggle button',
            )[0] as HTMLButtonElement
          ).click();
          fixture.detectChanges();
          await fixture.whenStable();

          expect(text()).toContain('Episodio anterior: solo lectura');
          expect(text()).toContain('Recuperado');
          expect(buttonTexts()).not.toContain('Registrar ahora');
          expect(buttonTexts().filter((label) => label === 'Corregir')).toHaveLength(0);
        });

        it('con un solo episodio no hay selector', async () => {
          await open(closedDetail());
          expect(toggles()).toEqual([]);
        });
      });
    });

    describe('mensaje para WhatsApp', () => {
      const messageButton = () =>
        Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
          b.textContent?.includes('Mensaje para WhatsApp'),
        ) as HTMLButtonElement | undefined;

      async function openedData(actions: string[]) {
        await open(detail({ allowedActions: actions }));
        const dialog = TestBed.inject(MatDialog);
        const spy = vi
          .spyOn(dialog, 'open')
          .mockReturnValue({ afterClosed: () => of(undefined) } as never);
        messageButton()?.click();
        await fixture.whenStable();
        expect(spy).toHaveBeenCalledWith(MessageDialog, expect.anything());
        return spy.mock.calls[0][1]?.data as { load: unknown; record?: unknown };
      }

      it('lo ven los cinco roles, tambien quien solo consulta', async () => {
        await open(detail({ allowedActions: [] }));
        expect(messageButton()).toBeDefined();
        await open(detail({ allowedActions: EDITOR_ACTIONS }));
        expect(messageButton()).toBeDefined();
      });

      it('quien solo consulta puede copiar el mensaje pero no registrar el envio', async () => {
        const data = await openedData([]);
        expect(data.load).toBeTypeOf('function');
        expect(data.record).toBeUndefined();
      });

      it('un editor puede ademas registrar el envio', async () => {
        const data = await openedData([...EDITOR_ACTIONS, 'COMMUNICATION']);
        expect(data.record).toBeTypeOf('function');
      });

      it('la seccion de comunicaciones esta siempre y la lista vacia no inventa envios', async () => {
        await open(detail());
        expect(text()).toContain('Comunicaciones');
        expect(text()).toContain('Todavía no se registró ningún envío');
      });
    });

    it('«Registrar ahora» solo aparece en los hitos T1 a T3 pendientes', async () => {
      await open(detail({ allowedActions: EDITOR_ACTIONS }));
      const quick = buttons().filter((label) => label === 'Registrar ahora');
      // T1 es el unico de T1 a T3 que sigue pendiente: T2 es N/A, T3 es S/D y T4 solo se registra al cerrar.
      expect(quick).toHaveLength(1);
    });

    it('«Registrar ahora» registra T1 con una sola llamada y muestra el detalle actualizado', async () => {
      await open(detail({ allowedActions: EDITOR_ACTIONS }));
      const button = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
      ).find((b) => b.textContent?.trim() === 'Registrar ahora') as HTMLButtonElement;
      button.click();

      const request = http.expectOne(`${URL}/episodes/1/milestones/T1`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ version: 0, status: 'RECORDED' });
      const base = detail({ allowedActions: EDITOR_ACTIONS });
      request.flush({
        ...base,
        summary: { ...base.summary, version: 1 },
        episodes: [
          {
            ...base.episodes[0],
            milestones: base.episodes[0].milestones.map((m) =>
              m.kind === 'T1'
                ? { ...m, status: 'RECORDED', occurredAt: '2026-11-21T15:10:00Z' }
                : m,
            ),
            durations: [{ pair: 'T1-T0', minutes: 10, missing: [] }],
          },
        ],
      });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fact('Validación (T1 − T0)')).toBe('0:10');
      expect(notifier.success).toHaveBeenCalledWith('T1 registrado');
      expect(buttons().filter((label) => label === 'Registrar ahora')).toHaveLength(0);
    });

    it('si otro usuario modifico el ticket, ofrece recargar en vez de sobrescribir', async () => {
      await open(detail({ allowedActions: EDITOR_ACTIONS }));
      const button = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
      ).find((b) => b.textContent?.trim() === 'Registrar ahora') as HTMLButtonElement;
      button.click();

      http
        .expectOne(`${URL}/episodes/1/milestones/T1`)
        .flush(
          { code: 'TICKET_VERSION_CONFLICT', detail: 'El ticket fue modificado por otro usuario' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();
      fixture.detectChanges();

      expect(notifier.error).toHaveBeenCalledWith('El ticket fue modificado por otro usuario');
      expect(text()).toContain('Otro usuario modificó este ticket');
      expect(buttons()).toContain('Recargar');
    });

    it('muestra las visitas con su etapa y, si se aplazo, quien lo decidio', async () => {
      await open(
        detail({
          visits: [
            {
              id: 'v1',
              episodeOrdinal: 1,
              providerName: 'Proveedor Uno',
              stage: 'POSTPONED',
              cancelDecidedBy: 'Supervisión',
              cancelEvidenceNote: 'Correo del proveedor',
              reescalationCondition: 'Si no hay fecha en 48 h',
              createdAt: '2026-11-21T15:00:00Z',
              updatedAt: '2026-11-21T16:00:00Z',
            },
          ],
        }),
      );
      expect(text()).toContain('Aplazada');
      expect(text()).toContain('Proveedor Uno');
      expect(text()).toContain('Decidió: Supervisión');
      expect(text()).toContain('Si no hay fecha en 48 h');
    });

    it('una visita que volvio a asignarse no muestra la decision de cuando estuvo aplazada', async () => {
      await open(
        detail({
          visits: [
            {
              id: 'v1',
              episodeOrdinal: 1,
              providerName: 'Proveedor Uno',
              stage: 'ASSIGNED',
              cancelDecidedBy: 'Supervisión',
              cancelEvidenceNote: 'Correo',
              reescalationCondition: 'Si no hay fecha',
              createdAt: '2026-11-21T15:00:00Z',
              updatedAt: '2026-11-21T16:00:00Z',
            },
          ],
        }),
      );
      expect(text()).toContain('Asignada');
      expect(text()).not.toContain('Decidió');
    });

    it('muestra las evidencias con su huella y marca la anulada', async () => {
      await open(
        detail({
          evidence: [
            {
              id: 'ev1',
              eventId: 'e1',
              purpose: 'GENERAL',
              format: 'PDF',
              sizeBytes: 2 * 1024 * 1024,
              sha256: 'a'.repeat(64),
              originalName: 'informe.pdf',
              uploadedByName: 'Ana Analista',
              uploadedAt: '2026-11-21T15:00:00Z',
              annulled: true,
            },
          ],
        }),
      );
      expect(text()).toContain('informe.pdf');
      expect(text()).toContain('PDF · 2,0 MB');
      expect(text()).toContain('SHA-256 aaaaaaaaaaaa…');
      expect(text()).toContain('anulada');
      const link = (fixture.nativeElement as HTMLElement).querySelector('a[href*="/evidence/ev1"]');
      expect(link?.getAttribute('href')).toBe(`${URL}/evidence/ev1`);
    });

    it('«Anular» solo se ofrece a los editores y en eventos que se pueden anular', async () => {
      const base = detail();
      const timeline = [
        ...base.timeline,
        {
          id: 'e2',
          type: 'OBSERVATION' as const,
          occurredAt: '2026-11-21T15:30:00Z',
          recordedAt: '2026-11-21T15:30:00Z',
          retroactive: false,
          details: {},
          annulled: false,
          text: 'Se observa estable',
        },
      ];
      await open(detail({ timeline, allowedActions: EDITOR_ACTIONS }));
      // Solo la observacion: «Ticket creado» lo genera el sistema y no se anula.
      expect(buttons().filter((label) => label === 'Anular')).toHaveLength(1);
    });
  });
});
