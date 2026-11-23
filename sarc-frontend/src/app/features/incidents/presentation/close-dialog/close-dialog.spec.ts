import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';

import {
  TicketCommunicationResponse,
  TicketEpisode,
  TicketEvidenceResponse,
  TicketSummaryResponse,
} from '../../../../core/api/generated';
import {
  alertText,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { CloseDialog } from './close-dialog';

type Harness = Awaited<ReturnType<typeof openDialog<CloseDialog>>>;

const T0_RECORDED = '2026-11-21T15:00:00Z';

function episode(overrides: Partial<TicketEpisode> = {}): TicketEpisode {
  const milestone = (kind: 'T0' | 'T1' | 'T2' | 'T3' | 'T4', status: string) =>
    ({
      kind,
      status,
      retroactive: false,
      orderWarningAck: false,
      recordedAt: T0_RECORDED,
    }) as TicketEpisode['milestones'][number];
  return {
    ordinal: 1,
    state: 'OPEN',
    milestones: [
      milestone('T0', 'RECORDED'),
      milestone('T1', 'RECORDED'),
      milestone('T2', 'NOT_APPLICABLE'),
      milestone('T3', 'NO_DATA'),
      milestone('T4', 'PENDING'),
    ],
    durations: [],
    references: [],
    ...overrides,
  };
}

function evidence(overrides: Partial<TicketEvidenceResponse> = {}): TicketEvidenceResponse {
  return {
    id: 'ev1',
    eventId: 'e1',
    purpose: 'FINAL',
    format: 'PDF',
    sizeBytes: 100,
    sha256: 'a'.repeat(64),
    originalName: 'cierre.pdf',
    uploadedAt: '2026-11-21T16:00:00Z',
    annulled: false,
    ...overrides,
  };
}

function communication(
  overrides: Partial<TicketCommunicationResponse> = {},
): TicketCommunicationResponse {
  return {
    id: 'c1',
    channel: 'EMAIL',
    kind: 'CLOSURE',
    sentAt: '2026-11-21T16:00:00Z',
    retroactive: false,
    recordedAt: '2026-11-21T16:00:00Z',
    annulled: false,
    ...overrides,
  };
}

function ticket(code: string): TicketSummaryResponse {
  return {
    id: `id-${code}`,
    code,
    state: 'OPEN',
    elementClass: 'EQUIPMENT',
    elementLabel: 'JAC Punto 1',
    incidentType: 'DISCONNECTION',
    incidentTypeLabel: 'Desconexión',
    priority: 'HIGH',
    responsible: { id: 'u1', name: 'Ana' },
    t0: T0_RECORDED,
    lastActivityAt: T0_RECORDED,
    episodeCount: 1,
    version: 0,
  };
}

function data(overrides: Record<string, unknown> = {}) {
  return {
    elementClass: 'EQUIPMENT',
    episode: episode(),
    evidence: [],
    communications: [],
    searchTickets: vi.fn().mockResolvedValue([]),
    save: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

async function choose(harness: Harness, label: string, option: string): Promise<void> {
  const field = Array.from(harness.element.querySelectorAll('mat-form-field')).find((f) =>
    f.querySelector('mat-label')?.textContent?.includes(label),
  );
  (field?.querySelector('mat-select') as HTMLElement).click();
  harness.fixture.detectChanges();
  // Los paneles de selectores anteriores pueden seguir en el DOM: se toma la opción del panel recién abierto.
  (
    Array.from(document.querySelectorAll('mat-option'))
      .filter((o) => o.textContent?.trim() === option)
      .at(-1) as HTMLElement
  ).click();
  harness.fixture.detectChanges();
  await harness.fixture.whenStable();
}

async function fillEquipment(harness: Harness): Promise<void> {
  await choose(harness, 'Resultado del cierre', 'Recuperado');
  await choose(harness, 'Causa', 'Confirmada');
  typeInto(harness.element, 'Descripción de la causa', 'Falla del enlace');
  await choose(harness, 'Plataforma en estado normal', 'Confirmada');
  await choose(harness, 'Estabilidad comprobada', 'No aplica');
  await choose(harness, 'Sin usuarios ni sesiones pendientes', 'Confirmada');
  await choose(harness, 'Prueba posterior', 'No aplica');
  typeInto(harness.element, 'Sin evidencia final: motivo', 'Sin captura');
  typeInto(harness.element, 'Sin comunicación de cierre: motivo', 'Por llamada');
  harness.fixture.detectChanges();
  await harness.fixture.whenStable();
  harness.fixture.detectChanges();
}

const text = (harness: Harness) => harness.element.textContent?.replace(/\s+/g, ' ') ?? '';
const submitButton = (harness: Harness) =>
  harness.element.querySelector('button[type="submit"]') as HTMLButtonElement;

describe('CloseDialog', () => {
  it('propone T4 con la hora actual sin escribirla y no pide motivo', async () => {
    const harness = await openDialog(CloseDialog, data());
    const t4 = harness.element.querySelector('input[type="datetime-local"]') as HTMLInputElement;
    expect(t4.value).toBe('');
    expect(text(harness)).toContain('Vacía, es el momento actual');
    expect(hasField(harness.element, 'Motivo de la hora anterior')).toBe(false);
  });

  it('una hora de cierre anterior pide su motivo', async () => {
    const harness = await openDialog(CloseDialog, data());
    typeInto(harness.element, 'Hora de cierre (T4)', '2020-01-01T10:00');
    harness.fixture.detectChanges();
    expect(hasField(harness.element, 'Motivo de la hora anterior')).toBe(true);
  });

  it('lista antes de enviar todo lo que falta y no deja cerrar', async () => {
    const harness = await openDialog(
      CloseDialog,
      data({
        episode: episode({
          milestones: episode().milestones.map((m) =>
            m.kind === 'T1' || m.kind === 'T3' ? { ...m, status: 'PENDING' } : m,
          ),
        }),
      }),
    );
    const content = text(harness);
    expect(content).toContain('Falta para cerrar');
    expect(content).toContain('T1: registre la primera validación');
    expect(content).toContain('T3: registre la recuperación');
    expect(content).toContain('El resultado del cierre');
    expect(content).toContain('La causa');
    expect(content).toContain('Las cuatro confirmaciones del protocolo');
    expect(content).toContain('Una evidencia final, o la justificación de su ausencia');
    expect(content).toContain('La comunicación de cierre registrada');
    expect(submitButton(harness).disabled).toBe(true);
  });

  it('un equipo pide las cuatro confirmaciones del protocolo', async () => {
    const harness = await openDialog(CloseDialog, data());
    for (const question of [
      'Plataforma en estado normal',
      'Estabilidad comprobada',
      'Sin usuarios ni sesiones pendientes',
      'Prueba posterior',
    ]) {
      expect(hasField(harness.element, question)).toBe(true);
    }
  });

  it('el chatbot y un elemento del sitio no piden las confirmaciones de cargadores', async () => {
    for (const elementClass of ['CHATBOT_SERVICE', 'SITE_ELEMENT']) {
      const harness = await openDialog(CloseDialog, data({ elementClass }));
      expect(hasField(harness.element, 'Plataforma en estado normal')).toBe(false);
      expect(text(harness)).not.toContain('Las cuatro confirmaciones del protocolo');
      expect(text(harness)).not.toContain('Confirmaciones del protocolo');
    }
  });

  it('completo, ya no falta nada y envía el cierre con las confirmaciones y las justificaciones', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(CloseDialog, data({ save }));

    await fillEquipment(harness);
    expect(text(harness)).toContain('No falta nada: puede cerrar el ticket.');
    expect(submitButton(harness).disabled).toBe(false);
    await submitDialog(harness);

    expect(save).toHaveBeenCalledTimes(1);
    expect(save.mock.calls[0][0]).toMatchObject({
      t4: null,
      result: 'RECOVERED',
      causeKind: 'CONFIRMED',
      causeText: 'Falla del enlace',
      closureChecks: {
        platformNormal: 'CONFIRMED',
        stabilityChecked: 'NOT_APPLICABLE',
        noAffectedUser: 'CONFIRMED',
        postTest: 'NOT_APPLICABLE',
      },
      finalEvidenceWaiver: 'Sin captura',
      closureCommunicationWaiver: 'Por llamada',
      deviationJustification: null,
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('una evidencia final y una comunicación de cierre del episodio reemplazan las justificaciones', async () => {
    const harness = await openDialog(
      CloseDialog,
      data({ evidence: [evidence()], communications: [communication()] }),
    );
    expect(text(harness)).toContain('Evidencia final: cargada.');
    expect(text(harness)).toContain('Comunicación de cierre: registrada.');
    expect(hasField(harness.element, 'Sin evidencia final: motivo')).toBe(false);
  });

  it('una evidencia anulada, de otro uso o de un episodio anterior no cuenta', async () => {
    const harness = await openDialog(
      CloseDialog,
      data({
        evidence: [
          evidence({ annulled: true }),
          evidence({ id: 'ev2', purpose: 'GENERAL' }),
          evidence({ id: 'ev3', uploadedAt: '2026-11-01T10:00:00Z' }),
        ],
        communications: [
          communication({ annulled: true }),
          communication({ id: 'c2', kind: 'UPDATE' }),
          communication({ id: 'c3', recordedAt: '2026-11-01T10:00:00Z' }),
        ],
      }),
    );
    expect(hasField(harness.element, 'Sin evidencia final: motivo')).toBe(true);
    expect(hasField(harness.element, 'Sin comunicación de cierre: motivo')).toBe(true);
  });

  it('una causa probable pide una evidencia del episodio y una no determinada no pide descripción', async () => {
    const harness = await openDialog(CloseDialog, data());
    await choose(harness, 'Causa', 'Probable');
    typeInto(harness.element, 'Descripción de la causa', 'Posible falla');
    expect(text(harness)).toContain('Una evidencia del episodio que respalde la causa probable');

    await choose(harness, 'Causa', 'No determinada');
    expect(hasField(harness.element, 'Descripción de la causa')).toBe(false);
    expect(text(harness)).not.toContain('causa probable');
  });

  it('fuera de la referencia pide la justificación de la desviación', async () => {
    const harness = await openDialog(
      CloseDialog,
      data({
        episode: episode({
          references: [
            { measure: 'RECOVERY', limitMinutes: 300, extendable: true, result: 'OUTSIDE' },
          ],
        }),
      }),
    );
    expect(hasField(harness.element, 'Justificación de la desviación')).toBe(true);
    expect(text(harness)).toContain('Referencia operativa, no SLA contractual');
    expect(text(harness)).toContain('La justificación de la desviación frente a la referencia');
  });

  it('un duplicado exige buscar el ticket original y pide poco más', async () => {
    const searchTickets = vi.fn().mockResolvedValue([ticket('TICKET-9002')]);
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(CloseDialog, data({ searchTickets, save }));

    await choose(harness, 'Resultado del cierre', 'Duplicado de otro ticket');
    await choose(harness, 'Causa', 'No determinada');
    expect(text(harness)).toContain('El ticket original del duplicado');
    expect(hasField(harness.element, 'Plataforma en estado normal')).toBe(false);
    expect(hasField(harness.element, 'Sin evidencia final: motivo')).toBe(false);
    expect(submitButton(harness).disabled).toBe(true);

    typeInto(harness.element, 'Ticket original', 'TICKET-9002');
    await vi.waitFor(() => expect(searchTickets).toHaveBeenCalledWith('TICKET-9002'));
    harness.fixture.detectChanges();
    (
      Array.from(harness.element.querySelectorAll('.candidates button')).find((b) =>
        b.textContent?.includes('TICKET-9002'),
      ) as HTMLElement
    ).click();
    harness.fixture.detectChanges();

    expect(text(harness)).toContain('Original elegido: TICKET-9002');
    expect(text(harness)).toContain('No falta nada');
    await submitDialog(harness);
    expect(save.mock.calls[0][0]).toMatchObject({
      result: 'DUPLICATE',
      duplicateOfTicketId: 'id-TICKET-9002',
      causeKind: 'UNDETERMINED',
    });
  });

  it('un registrado por error no pide T1 a T3, confirmaciones, evidencia ni comunicación', async () => {
    const harness = await openDialog(
      CloseDialog,
      data({
        episode: episode({
          milestones: episode().milestones.map((m) =>
            ['T1', 'T2', 'T3'].includes(m.kind) ? { ...m, status: 'PENDING' } : m,
          ),
        }),
      }),
    );
    await choose(harness, 'Resultado del cierre', 'Registrado por error');
    await choose(harness, 'Causa', 'No determinada');
    expect(text(harness)).toContain('No falta nada');
    expect(submitButton(harness).disabled).toBe(false);
  });

  it('si el servidor rechaza el cierre muestra su lista de faltantes con textos claros y no cierra', async () => {
    const save = vi.fn().mockRejectedValue(
      new HttpErrorResponse({
        status: 422,
        error: {
          code: 'TICKET_CLOSE_REQUIREMENTS',
          detail: 'Faltan requisitos para cerrar el ticket: FINAL_EVIDENCE',
          missing: ['FINAL_EVIDENCE', 'CLOSURE_COMMUNICATION'],
        },
      }),
    );
    const harness = await openDialog(CloseDialog, data({ save }));
    await fillEquipment(harness);

    await submitDialog(harness);

    expect(alertText(harness.element)).toContain('Faltan requisitos para cerrar el ticket');
    expect(harness.element.querySelector('.form-alert')?.textContent).toContain(
      'Una evidencia final, o la justificación de su ausencia',
    );
    expect(harness.element.querySelector('.form-alert')?.textContent).toContain(
      'La comunicación de cierre registrada',
    );
    expect(harness.close).not.toHaveBeenCalled();
  });
});
