import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, afterEach, beforeEach, vi } from 'vitest';

import { TicketDetailResponse } from '../../../core/api/generated';
import { TicketDetailFacade } from './ticket-detail-facade';

const URL = '/api/v1/incidents/tickets/id-1';

function detail(version: number): TicketDetailResponse {
  return {
    summary: {
      id: 'id-1',
      code: 'TICKET-9001',
      state: 'OPEN',
      elementClass: 'EQUIPMENT',
      elementLabel: 'KIA Norte',
      incidentType: 'DISCONNECTION',
      incidentTypeLabel: 'Desconexión',
      priority: 'HIGH',
      responsible: { id: 'u1', name: 'Ana Analista' },
      t0: '2026-11-21T15:00:00Z',
      lastActivityAt: '2026-11-21T15:00:00Z',
      episodeCount: 1,
      version,
    },
    description: 'Sin comunicación',
    reportOrigin: 'PLATFORM',
    connectors: [],
    episodes: [],
    visits: [],
    evidence: [],
    communications: [],
    relations: [],
    timeline: [],
    allowedActions: ['MILESTONE'],
  };
}

describe('TicketDetailFacade', () => {
  let facade: TicketDetailFacade;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [TicketDetailFacade, provideHttpClient(), provideHttpClientTesting()],
    });
    facade = TestBed.inject(TicketDetailFacade);
    http = TestBed.inject(HttpTestingController);
    const opening = facade.open('id-1');
    http.expectOne(URL).flush(detail(3));
    await opening;
  });

  afterEach(() => http.verify());

  it('cada escritura envia la version del detalle y lo reemplaza con la respuesta', async () => {
    const saving = facade.recordMilestone(1, 'T1', { status: 'RECORDED' });

    const request = http.expectOne(`${URL}/episodes/1/milestones/T1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ version: 3, status: 'RECORDED' });
    request.flush(detail(4));
    await saving;

    expect(facade.detail()?.summary.version).toBe(4);
  });

  it('cada accion usa su ruta y su cuerpo con la version', async () => {
    const cases: [() => Promise<void>, string, string, object][] = [
      [
        () => facade.addEvent({ type: 'OBSERVATION', text: 'x' }),
        'POST',
        `${URL}/events`,
        { version: 3, type: 'OBSERVATION', text: 'x' },
      ],
      [
        () => facade.annulEvent('e1', { reason: 'r' }),
        'POST',
        `${URL}/events/e1/annul`,
        { version: 3, reason: 'r' },
      ],
      [
        () => facade.changePriority({ priority: 'CRITICAL', reason: 'r' }),
        'POST',
        `${URL}/priority`,
        { version: 3, priority: 'CRITICAL', reason: 'r' },
      ],
      [
        () => facade.assignResponsible('u2'),
        'POST',
        `${URL}/responsible`,
        { version: 3, responsibleId: 'u2' },
      ],
      [
        () => facade.requestVisit({ providerName: 'P' }),
        'POST',
        `${URL}/visits`,
        { version: 3, providerName: 'P' },
      ],
      [
        () => facade.moveVisitStage('v1', { stage: 'ASSIGNED' }),
        'POST',
        `${URL}/visits/v1/stage`,
        { version: 3, stage: 'ASSIGNED' },
      ],
      [
        () => facade.correctData({ description: 'd', justification: 'j' }),
        'PATCH',
        URL,
        { version: 3, description: 'd', justification: 'j' },
      ],
    ];
    for (const [call, method, url, body] of cases) {
      const saving = call();
      const request = http.expectOne((r) => r.url === url && r.method === method);
      expect(request.request.body).toEqual(body);
      request.flush(detail(3));
      await saving;
    }
  });

  it('una evidencia se carga como multipart con las partes file y meta', async () => {
    const saving = facade.uploadEvidence(new File(['%PDF-'], 'informe.pdf'), { purpose: 'FINAL' });

    const request = http.expectOne(`${URL}/evidence`);
    const body = request.request.body as FormData;
    expect((body.get('file') as File).name).toBe('informe.pdf');
    expect(body.get('meta')).toBeInstanceOf(Blob);
    expect(JSON.parse(await (body.get('meta') as Blob).text())).toEqual({
      version: 3,
      purpose: 'FINAL',
    });
    request.flush(detail(4));
    await saving;
  });

  it('un 409 marca el conflicto, conserva el detalle y relanza el error para el dialogo', async () => {
    const saving = facade.addEvent({ type: 'OBSERVATION', text: 'x' });
    http
      .expectOne(`${URL}/events`)
      .flush(
        { code: 'TICKET_VERSION_CONFLICT', detail: 'Modificado por otro usuario' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(saving).rejects.toBeDefined();
    expect(facade.conflict()).toBe(true);
    expect(facade.detail()?.summary.version).toBe(3);
  });

  it('otro error no marca conflicto, y recargar lo limpia', async () => {
    const failing = facade.addEvent({ type: 'OBSERVATION', text: 'x' });
    http
      .expectOne(`${URL}/events`)
      .flush({ code: 'TICKET_EVENT_INVALID', detail: 'x' }, { status: 422, statusText: 'x' });
    await expect(failing).rejects.toBeDefined();
    expect(facade.conflict()).toBe(false);

    const conflicting = facade.addEvent({ type: 'OBSERVATION', text: 'x' });
    http
      .expectOne(`${URL}/events`)
      .flush({ code: 'TICKET_VERSION_CONFLICT', detail: 'x' }, { status: 409, statusText: 'x' });
    await expect(conflicting).rejects.toBeDefined();
    expect(facade.conflict()).toBe(true);

    const reloading = facade.reload();
    http.expectOne(URL).flush(detail(5));
    await reloading;
    expect(facade.conflict()).toBe(false);
    expect(facade.detail()?.summary.version).toBe(5);
  });

  it('una recarga enviada antes de una escritura no pisa el detalle recien guardado', async () => {
    const reloading = facade.reload();
    const stale = http.expectOne(URL);
    const saving = facade.addEvent({ type: 'OBSERVATION', text: 'x' });
    http.expectOne(`${URL}/events`).flush(detail(4));
    await saving;

    stale.flush(detail(3));
    await reloading;

    expect(facade.detail()?.summary.version).toBe(4);
  });

  it('la direccion de descarga de una evidencia apunta al endpoint del ticket abierto', () => {
    expect(facade.evidenceUrl('ev1')).toBe(`${URL}/evidence/ev1`);
  });

  it('registrar un envio manda el canal, el tipo y la hora con la version, sin evidencia', async () => {
    const saving = facade.recordCommunication(
      { channel: 'EMAIL', kind: 'INITIAL', sentAt: '2026-11-22T15:00:00Z' },
      null,
    );
    const request = http.expectOne(`${URL}/communications`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      version: 3,
      channel: 'EMAIL',
      kind: 'INITIAL',
      sentAt: '2026-11-22T15:00:00Z',
    });
    request.flush(detail(4));

    expect(await saving).toBeNull();
    http.expectNone(`${URL}/evidence`);
  });

  it('si hay evidencia la liga a la comunicacion recien registrada con la version nueva', async () => {
    const saving = facade.recordCommunication(
      { channel: 'EMAIL', kind: 'UPDATE', sentAt: '2026-11-22T15:00:00Z' },
      new File(['%PDF-'], 'captura.pdf'),
    );
    http.expectOne(`${URL}/communications`).flush({
      ...detail(4),
      communications: [
        { id: 'c-old', recordedAt: '2026-11-22T10:00:00Z' },
        { id: 'c-new', recordedAt: '2026-11-22T15:00:00Z' },
      ],
    });
    const upload = await vi.waitFor(() => http.expectOne(`${URL}/evidence`));
    const meta = JSON.parse(await ((upload.request.body as FormData).get('meta') as Blob).text());
    expect(meta).toEqual({ version: 4, purpose: 'GENERAL', communicationId: 'c-new' });
    upload.flush(detail(5));

    expect(await saving).toBeNull();
  });

  it('si la evidencia falla el envio queda registrado y se devuelve el motivo, sin repetirlo', async () => {
    const saving = facade.recordCommunication(
      { channel: 'EMAIL', kind: 'UPDATE', sentAt: '2026-11-22T15:00:00Z' },
      new File(['x'], 'captura.pdf'),
    );
    http.expectOne(`${URL}/communications`).flush({
      ...detail(4),
      communications: [{ id: 'c-new', recordedAt: '2026-11-22T15:00:00Z' }],
    });
    const upload = await vi.waitFor(() => http.expectOne(`${URL}/evidence`));
    upload.flush(
      { code: 'EVIDENCE_FORMAT_NOT_ALLOWED', detail: 'Formato no permitido' },
      { status: 422, statusText: 'x' },
    );

    expect(await saving).toBe('Formato no permitido');
    expect(facade.detail()?.summary.version).toBe(4);
    http.expectNone(`${URL}/communications`);
  });

  it('el mensaje se pide por GET con las opciones y no cambia el detalle', async () => {
    const loading = facade.message({ includeDetection: true, includeActions: false });
    const request = http.expectOne((r) => r.url === `${URL}/message`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('includeDetection')).toBe('true');
    expect(request.request.params.get('includeActions')).toBe('false');
    request.flush({ text: 'x', missingFields: [], ticketCode: 'TICKET-9001', state: 'OPEN' });

    expect((await loading).text).toBe('x');
    expect(facade.detail()?.summary.version).toBe(3);
  });

  it('cerrar manda el cuerpo con la version y reemplaza el detalle con el ticket cerrado', async () => {
    const saving = facade.closeTicket({ result: 'RECOVERED', causeKind: 'UNDETERMINED' });
    const request = http.expectOne(`${URL}/close`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      version: 3,
      result: 'RECOVERED',
      causeKind: 'UNDETERMINED',
    });
    request.flush({ ...detail(4), summary: { ...detail(4).summary, state: 'CLOSED' } });
    await saving;

    expect(facade.detail()?.summary.state).toBe('CLOSED');
  });

  it('si faltan requisitos el 422 se relanza con la lista y el detalle no cambia', async () => {
    const saving = facade.closeTicket({ result: 'RECOVERED' });
    http.expectOne(`${URL}/close`).flush(
      {
        code: 'TICKET_CLOSE_REQUIREMENTS',
        detail: 'Faltan requisitos',
        missing: ['T1', 'CAUSE'],
      },
      { status: 422, statusText: 'x' },
    );

    await expect(saving).rejects.toMatchObject({ error: { missing: ['T1', 'CAUSE'] } });
    expect(facade.detail()?.summary.version).toBe(3);
    expect(facade.conflict()).toBe(false);
  });

  it('reabrir manda el motivo y la hora con la version', async () => {
    const saving = facade.reopenTicket({ reason: 'La falla volvió', t0: null, t0Reason: null });
    const request = http.expectOne(`${URL}/reopen`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      version: 3,
      reason: 'La falla volvió',
      t0: null,
      t0Reason: null,
    });
    request.flush(detail(4));
    await saving;
    expect(facade.detail()?.summary.version).toBe(4);
  });

  it('buscar el original de un duplicado excluye al propio ticket', async () => {
    const searching = facade.searchTickets(' TICKET-90 ');
    const request = http.expectOne((r) => r.url === '/api/v1/incidents/tickets');
    expect(request.request.params.get('search')).toBe('TICKET-90');
    expect(request.request.params.get('size')).toBe('8');
    request.flush({
      items: [
        { id: 'id-1', code: 'TICKET-9001' },
        { id: 'id-2', code: 'TICKET-9002' },
      ],
      total: 2,
      page: 0,
      size: 8,
      cutOff: '2026-11-22T00:00:00Z',
    });

    expect((await searching).map((item) => item.code)).toEqual(['TICKET-9002']);
  });
});
