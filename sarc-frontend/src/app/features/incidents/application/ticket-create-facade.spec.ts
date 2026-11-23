import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { IncidentAssetResponse, TicketDetailResponse } from '../../../core/api/generated';
import { TicketCreateFacade } from './ticket-create-facade';

const BASE = '/api/v1/incidents';

const STATION: IncidentAssetResponse = {
  id: 'eq-1',
  name: 'KIA Norte',
  network: 'KIA',
  infrastructureType: 'CHARGING_STATION',
  connectors: [
    { id: 'c1', position: 1, typeLabel: 'CCS2', active: true },
    { id: 'c2', position: 2, typeLabel: 'Tipo 2', active: true },
    { id: 'c3', position: 3, typeLabel: 'CCS2', active: false },
  ],
};

describe('TicketCreateFacade', () => {
  let http: HttpTestingController;
  let facade: TicketCreateFacade;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TicketCreateFacade],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(TicketCreateFacade);
  });

  afterEach(() => http.verify());

  async function choose(asset: IncidentAssetResponse = STATION, open: unknown[] = []) {
    const choosing = facade.selectAsset(asset);
    http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush(open);
    await choosing;
  }

  it('busca equipos activos con el texto escrito', async () => {
    const searching = facade.searchAssets(' kia ');
    const request = http.expectOne((r) => r.url === `${BASE}/assets`);
    expect(request.request.params.get('search')).toBe('kia');
    request.flush([STATION]);
    await searching;
    expect(facade.results()).toEqual([STATION]);
    expect(facade.searching()).toBe(false);
  });

  it('elegir un equipo lo deja completo y busca sus tickets abiertos', async () => {
    const choosing = facade.selectAsset(STATION);
    const request = http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`);
    expect(request.request.params.get('elementClass')).toBe('EQUIPMENT');
    expect(request.request.params.get('equipmentId')).toBe('eq-1');
    request.flush([
      { id: 't1', code: 'TICKET-9001', incidentTypeLabel: 'Desconexión', priority: 'HIGH' },
    ]);
    await choosing;

    expect(facade.asset()?.name).toBe('KIA Norte');
    expect(facade.wholeEquipment()).toBe(true);
    expect(facade.hasMatches()).toBe(true);
    expect(facade.activeConnectors().map((c) => c.id)).toEqual(['c1', 'c2']);
  });

  it('elegir conectores deja de ser equipo completo, y desmarcarlos lo restituye', async () => {
    await choose();
    facade.toggleConnector('c2');
    expect(facade.connectorIds()).toEqual(['c2']);
    expect(facade.wholeEquipment()).toBe(false);
    facade.toggleConnector('c2');
    expect(facade.wholeEquipment()).toBe(true);
    facade.toggleConnector('c1');
    facade.setWholeEquipment();
    expect(facade.connectorIds()).toEqual([]);
  });

  it('crea con los tres datos y el equipo completo, sin enviar vacios', async () => {
    await choose();
    const submitting = facade.submit({
      incidentType: 'DISCONNECTION',
      description: ' Sin comunicación ',
    });
    const request = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
    expect(request.request.body).toMatchObject({
      elementClass: 'EQUIPMENT',
      equipmentId: 'eq-1',
      connectorIds: [],
      incidentType: 'DISCONNECTION',
      description: 'Sin comunicación',
      priorityReason: null,
      t0: null,
      duplicateOverrideReason: null,
    });
    expect(typeof request.request.body.clientRequestId).toBe('string');
    request.flush({ summary: { code: 'TICKET-9001' } } as unknown as TicketDetailResponse);
    expect((await submitting)?.summary.code).toBe('TICKET-9001');
    expect(facade.submitting()).toBe(false);
  });

  it('repite el clientRequestId en un reintento y lo renueva tras crear', async () => {
    await choose();
    const first = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
    const firstRequest = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
    const sentId = firstRequest.request.body.clientRequestId;
    firstRequest.flush({ code: 'X', detail: 'Falla' }, { status: 500, statusText: 'Error' });
    expect(await first).toBeNull();

    const retry = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
    const retryRequest = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
    expect(retryRequest.request.body.clientRequestId).toBe(sentId);
    retryRequest.flush({ summary: { code: 'TICKET-9001' } } as unknown as TicketDetailResponse);
    await retry;

    const next = facade.submit({ incidentType: 'DISCONNECTION', description: 'y' });
    const nextRequest = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
    expect(nextRequest.request.body.clientRequestId).not.toBe(sentId);
    nextRequest.flush({ summary: { code: 'TICKET-9002' } } as unknown as TicketDetailResponse);
    await next;
  });

  it('un aviso de duplicado del servidor llena la lista y no crea nada', async () => {
    await choose();
    const submitting = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
    http.expectOne({ url: `${BASE}/tickets`, method: 'POST' }).flush(
      {
        code: 'TICKET_OPEN_DUPLICATE',
        detail: 'Ya hay tickets abiertos para este elemento',
        openTickets: [
          { id: 't1', code: 'TICKET-9001', incidentTypeLabel: 'Desconexión', priority: 'HIGH' },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(await submitting).toBeNull();
    expect(facade.matches().map((m) => m.code)).toEqual(['TICKET-9001']);
    expect(facade.error()).toContain('Ya hay tickets abiertos');
  });

  it('distingue la numeracion sin configurar del ambiente distinto', async () => {
    await choose();
    for (const [code, block] of [
      ['TICKET_NUMBERING_NOT_CONFIGURED', 'NOT_CONFIGURED'],
      ['TICKET_NUMBERING_ENVIRONMENT_MISMATCH', 'ENVIRONMENT_MISMATCH'],
    ] as const) {
      const submitting = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
      http
        .expectOne({ url: `${BASE}/tickets`, method: 'POST' })
        .flush({ code, detail: 'Numeración' }, { status: 409, statusText: 'Conflict' });
      await submitting;
      expect(facade.numberingBlock()).toBe(block);
    }
  });

  it('sin equipo elegido no envia nada', async () => {
    expect(await facade.submit({ incidentType: 'DISCONNECTION', description: 'x' })).toBeNull();
    http.expectNone({ url: `${BASE}/tickets`, method: 'POST' });
    expect(facade.error()).toContain('Elija el equipo');
  });

  describe('elementos sin cargador', () => {
    const OPEN = [
      { id: 't1', code: 'TICKET-9001', incidentTypeLabel: 'Falla', priority: 'MEDIUM' },
    ];

    /** Cambia de clase y responde la busqueda de avisos que dispara el chatbot. */
    async function switchTo(elementClass: 'CHATBOT_SERVICE' | 'SITE_ELEMENT' | 'EQUIPMENT') {
      facade.setElementClass(elementClass);
      if (elementClass === 'CHATBOT_SERVICE') {
        http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush([]);
        await Promise.resolve();
      }
    }

    function createdBody(): Record<string, unknown> {
      const request = http.expectOne({ url: `${BASE}/tickets`, method: 'POST' });
      request.flush({ summary: { code: 'TICKET-9002' } } as unknown as TicketDetailResponse);
      return request.request.body;
    }

    it('el chatbot se crea sin equipo, conectores ni elemento del sitio, y el alcance es opcional', async () => {
      await switchTo('CHATBOT_SERVICE');
      const first = facade.submit({ incidentType: 'CHATBOT_SERVICE_FAILURE', description: 'x' });
      const body = createdBody();
      await first;

      expect(body).toMatchObject({
        elementClass: 'CHATBOT_SERVICE',
        incidentType: 'CHATBOT_SERVICE_FAILURE',
      });
      expect(body).not.toHaveProperty('equipmentId');
      expect(body).not.toHaveProperty('connectorIds');
      expect(body).not.toHaveProperty('siteElement');
      expect(body).not.toHaveProperty('chatbotScope');

      facade.chatbotScope.set('PARTIAL');
      const second = facade.submit({ incidentType: 'CHATBOT_SERVICE_FAILURE', description: 'y' });
      expect(createdBody()).toMatchObject({ chatbotScope: 'PARTIAL' });
      await second;
    });

    it('al elegir el chatbot busca de inmediato los tickets abiertos del servicio', async () => {
      facade.setElementClass('CHATBOT_SERVICE');
      const request = http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`);
      expect(request.request.params.get('elementClass')).toBe('CHATBOT_SERVICE');
      request.flush(OPEN);
      await vi.waitFor(() => expect(facade.hasMatches()).toBe(true));
    });

    it('cambiar de clase no arrastra el equipo, los conectores, los avisos ni el error', async () => {
      await choose(STATION, OPEN);
      facade.toggleConnector('c2');
      expect(facade.hasMatches()).toBe(true);

      await switchTo('CHATBOT_SERVICE');

      expect(facade.asset()).toBeNull();
      expect(facade.connectorIds()).toEqual([]);
      expect(facade.hasMatches()).toBe(false);
      expect(facade.error()).toBe('');
    });

    it('una barrera con equipo de referencia envia solo el equipo de referencia, sin conectores', async () => {
      facade.setElementClass('SITE_ELEMENT');
      facade.siteElementName.set(' Entrada ');
      const choosing = facade.selectAsset(STATION);
      const matches = http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`);
      expect(matches.request.params.get('elementClass')).toBe('SITE_ELEMENT');
      expect(matches.request.params.get('siteElementKind')).toBe('VEHICLE_BARRIER');
      expect(matches.request.params.get('referenceEquipmentId')).toBe('eq-1');
      expect(matches.request.params.get('elementName')).toBe('Entrada');
      expect(matches.request.params.has('equipmentId')).toBe(false);
      matches.flush([]);
      await choosing;

      const submitting = facade.submit({ incidentType: 'SITE_ELEMENT_FAILURE', description: 'x' });
      const body = createdBody();
      await submitting;

      expect(body).toMatchObject({
        elementClass: 'SITE_ELEMENT',
        siteElement: {
          kind: 'VEHICLE_BARRIER',
          elementName: 'Entrada',
          referenceEquipmentId: 'eq-1',
        },
      });
      expect(body).not.toHaveProperty('equipmentId');
      expect(body).not.toHaveProperty('connectorIds');
    });

    it('un sitio escrito a mano envia el sitio y la ubicacion, con Maps vacio como nulo', async () => {
      facade.setElementClass('SITE_ELEMENT');
      facade.setSiteLocationMode('MANUAL');
      facade.siteKind.set('OTHER');
      facade.siteElementName.set('Portón peatonal');
      facade.manualSiteName.set(' Sitio sin equipos ');
      facade.manualLocation.set('Acceso norte');

      const submitting = facade.submit({ incidentType: 'SITE_ELEMENT_FAILURE', description: 'x' });
      const body = createdBody();
      await submitting;

      expect(body).toMatchObject({
        elementClass: 'SITE_ELEMENT',
        siteElement: {
          kind: 'OTHER',
          elementName: 'Portón peatonal',
          manualSiteName: 'Sitio sin equipos',
          manualLocation: 'Acceso norte',
          manualMapsUrl: null,
        },
      });
      expect(JSON.stringify(body)).not.toContain('referenceEquipmentId');
    });

    it('busca avisos del sitio escrito a mano con su nombre, y no antes de tenerlo', async () => {
      facade.setElementClass('SITE_ELEMENT');
      facade.setSiteLocationMode('MANUAL');
      await facade.refreshMatches();
      http.expectNone((r) => r.url === `${BASE}/tickets/open-matches`);

      facade.manualSiteName.set('Sitio sin equipos');
      facade.siteElementName.set('Entrada');
      const refreshing = facade.refreshMatches();
      const request = http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`);
      expect(request.request.params.get('manualSiteName')).toBe('Sitio sin equipos');
      expect(request.request.params.get('elementName')).toBe('Entrada');
      request.flush(OPEN);
      await refreshing;
      expect(facade.hasMatches()).toBe(true);
    });

    it('un elemento del sitio incompleto no se envia y dice que falta', async () => {
      facade.setElementClass('SITE_ELEMENT');

      expect(
        await facade.submit({ incidentType: 'SITE_ELEMENT_FAILURE', description: 'x' }),
      ).toBeNull();
      expect(facade.error()).toBe('Elija el equipo de referencia del sitio.');

      facade.siteKind.set('OTHER');
      expect(
        await facade.submit({ incidentType: 'SITE_ELEMENT_FAILURE', description: 'x' }),
      ).toBeNull();
      expect(facade.error()).toBe('Escriba el nombre del elemento del sitio.');

      facade.siteElementName.set('Portón');
      facade.setSiteLocationMode('MANUAL');
      expect(
        await facade.submit({ incidentType: 'SITE_ELEMENT_FAILURE', description: 'x' }),
      ).toBeNull();
      expect(facade.error()).toBe('Escriba el nombre del sitio y su ubicación.');
      http.expectNone({ url: `${BASE}/tickets`, method: 'POST' });
    });

    it('volver a equipo exige elegir el equipo otra vez', async () => {
      await choose();
      await switchTo('CHATBOT_SERVICE');
      await switchTo('EQUIPMENT');
      expect(await facade.submit({ incidentType: 'DISCONNECTION', description: 'x' })).toBeNull();
      expect(facade.error()).toContain('Elija el equipo');
    });
  });

  describe('evidencia inicial', () => {
    const created = {
      summary: { id: 't1', code: 'TICKET-9001', version: 2 },
    } as unknown as TicketDetailResponse;

    it('sin archivo no llama al endpoint de evidencias', async () => {
      await choose();
      const submitting = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
      http.expectOne({ url: `${BASE}/tickets`, method: 'POST' }).flush(created);

      expect((await submitting)?.summary.code).toBe('TICKET-9001');
      http.expectNone(`${BASE}/tickets/t1/evidence`);
      expect(facade.evidenceWarning()).toBe('');
    });

    it('carga el archivo como evidencia inicial con la version del ticket recien creado', async () => {
      await choose();
      facade.evidenceFile.set(new File(['%PDF-'], 'captura.pdf'));
      const submitting = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
      http.expectOne({ url: `${BASE}/tickets`, method: 'POST' }).flush(created);
      const upload = await vi.waitFor(() => http.expectOne(`${BASE}/tickets/t1/evidence`));
      expect(((upload.request.body as FormData).get('file') as File).name).toBe('captura.pdf');
      upload.flush(created);

      expect((await submitting)?.summary.code).toBe('TICKET-9001');
      expect(facade.evidenceWarning()).toBe('');
      expect(facade.evidenceFile()).toBeNull();
    });

    it('si la evidencia falla, el ticket queda creado y se guarda el motivo para avisar', async () => {
      await choose();
      facade.evidenceFile.set(new File(['x'], 'captura.pdf'));
      const submitting = facade.submit({ incidentType: 'DISCONNECTION', description: 'x' });
      http.expectOne({ url: `${BASE}/tickets`, method: 'POST' }).flush(created);
      const upload = await vi.waitFor(() => http.expectOne(`${BASE}/tickets/t1/evidence`));
      const meta = JSON.parse(await ((upload.request.body as FormData).get('meta') as Blob).text());
      expect(meta).toEqual({ version: 2, purpose: 'INITIAL' });
      upload.flush(
        { code: 'EVIDENCE_FORMAT_NOT_ALLOWED', detail: 'Formato no permitido' },
        { status: 422, statusText: 'x' },
      );

      expect((await submitting)?.summary.code).toBe('TICKET-9001');
      expect(facade.evidenceWarning()).toBe('Formato no permitido');
      expect(facade.evidenceFile()).toBeNull();
      expect(facade.error()).toBe('');
    });
  });
});
