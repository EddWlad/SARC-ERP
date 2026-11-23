import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  CreateTicketRequest,
  IncidentAssetResponse,
  OpenMatchResponse,
  SiteElementRequest,
  TicketDetailResponse,
} from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IncidentsApi, OpenMatchQuery } from '../data-access/incidents-api';
import {
  ChatbotScope,
  ElementClass,
  IncidentType,
  Priority,
  ReportOrigin,
  SiteElementKind,
} from '../domain/incident-labels';

/** Donde esta un elemento del sitio: junto a un equipo del inventario o en un sitio escrito a mano (FR-001a). */
export type SiteLocationMode = 'REFERENCE' | 'MANUAL';

/** Lo que el analista escribe al crear un ticket (FR-010): tres datos y, si hace falta, los demas. */
export interface TicketInput {
  readonly incidentType: IncidentType;
  readonly description: string;
  readonly reportOrigin?: ReportOrigin;
  readonly reportOriginDetail?: string;
  readonly priority?: Priority;
  readonly priorityReason?: string;
  /** Instante ISO de T0; vacio es el momento de creacion. */
  readonly t0?: string;
  readonly t0Reason?: string;
  readonly duplicateOverrideReason?: string;
}

/** Por que la numeracion impide crear: sin configurar, o con el ambiente del servidor distinto (research R2). */
export type NumberingBlock = 'NOT_CONFIGURED' | 'ENVIRONMENT_MISMATCH';

/**
 * Creacion de un ticket: la clase de elemento (equipo, servicio de chatbot o elemento del sitio), la busqueda del
 * equipo, los conectores elegidos, el aviso de duplicado en linea y el envio. El `clientRequestId` se genera al abrir el formulario y se repite en los reintentos, de modo que un doble clic
 * o un reenvio no crea dos tickets ni consume dos numeros (FR-015).
 */
@Service({ autoProvided: false })
export class TicketCreateFacade {
  private readonly api = inject(IncidentsApi);
  private searchTicket = 0;
  private matchTicket = 0;

  /** Una clave por formulario; se renueva al crear con exito. */
  private clientRequestId: string = crypto.randomUUID();

  /** Clase del elemento afectado. Solo un equipo tiene equipo, conectores y red (FR-001, FR-005). */
  readonly elementClass = signal<ElementClass>('EQUIPMENT');
  /** Alcance de una falla del chatbot; vacio si no se indica. */
  readonly chatbotScope = signal<ChatbotScope | ''>('');
  readonly siteKind = signal<SiteElementKind>('VEHICLE_BARRIER');
  readonly siteElementName = signal('');
  readonly siteLocationMode = signal<SiteLocationMode>('REFERENCE');
  readonly manualSiteName = signal('');
  readonly manualLocation = signal('');
  readonly manualMapsUrl = signal('');

  readonly results = signal<readonly IncidentAssetResponse[]>([]);
  readonly searching = signal(false);
  readonly asset = signal<IncidentAssetResponse | null>(null);
  /** Conectores elegidos; vacio significa el equipo completo (FR-002). */
  readonly connectorIds = signal<readonly string[]>([]);
  /** Tickets abiertos del mismo equipo (FR-016). */
  readonly matches = signal<readonly OpenMatchResponse[]>([]);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly numberingBlock = signal<NumberingBlock | null>(null);

  /** Evidencia inicial elegida (FR-037): se carga justo después de crear el ticket. */
  readonly evidenceFile = signal<File | null>(null);
  /** Si el ticket se creó pero la evidencia inicial no se pudo cargar, el motivo; el ticket no se pierde. */
  readonly evidenceWarning = signal('');
  readonly wholeEquipment = computed(() => this.connectorIds().length === 0);
  readonly hasMatches = computed(() => this.matches().length > 0);
  readonly activeConnectors = computed(
    () => this.asset()?.connectors.filter((connector) => connector.active) ?? [],
  );

  async searchAssets(text: string): Promise<void> {
    const ticket = ++this.searchTicket;
    this.searching.set(true);
    try {
      const found = await firstValueFrom(this.api.assets(text.trim()));
      if (ticket === this.searchTicket) this.results.set(found);
    } catch (error) {
      if (ticket === this.searchTicket) {
        this.results.set([]);
        this.error.set(toProblem(error).detail);
      }
    } finally {
      if (ticket === this.searchTicket) this.searching.set(false);
    }
  }

  /**
   * Cambia la clase del elemento. No se arrastra nada de la clase anterior: el equipo, los conectores, los avisos y el
   * error se limpian, para que un chatbot o una barrera nunca hereden un equipo (FR-005). El chatbot busca de inmediato
   * si ya hay un ticket abierto, porque su clave no depende de nada mas.
   */
  setElementClass(elementClass: ElementClass): void {
    if (elementClass === this.elementClass()) return;
    this.elementClass.set(elementClass);
    this.asset.set(null);
    this.connectorIds.set([]);
    this.matches.set([]);
    this.error.set('');
    this.numberingBlock.set(null);
    void this.refreshMatches();
  }

  /** Elige el equipo (completo por defecto) o, en un elemento del sitio, el equipo de referencia; busca avisos. */
  async selectAsset(asset: IncidentAssetResponse): Promise<void> {
    this.asset.set(asset);
    this.connectorIds.set([]);
    this.matches.set([]);
    this.error.set('');
    await this.refreshMatches();
  }

  clearAsset(): void {
    this.asset.set(null);
    this.connectorIds.set([]);
    this.matches.set([]);
    this.matchTicket++;
  }

  setSiteLocationMode(mode: SiteLocationMode): void {
    if (mode === this.siteLocationMode()) return;
    this.siteLocationMode.set(mode);
    this.asset.set(null);
    this.matches.set([]);
    this.matchTicket++;
  }

  /**
   * Busca los tickets abiertos del mismo elemento (FR-016). La clave depende de la clase: el equipo, el chatbot (uno
   * solo) o el sitio con su clase y nombre. Es una ayuda: si falla, el servidor lo vuelve a comprobar al crear.
   */
  async refreshMatches(): Promise<void> {
    const query = this.matchQuery();
    const ticket = ++this.matchTicket;
    if (query === null) {
      this.matches.set([]);
      return;
    }
    try {
      const open = await firstValueFrom(this.api.openMatches(query));
      if (ticket === this.matchTicket) this.matches.set(open);
    } catch {
      // Sin aviso: el servidor lo vuelve a comprobar al crear.
    }
  }

  private matchQuery(): OpenMatchQuery | null {
    switch (this.elementClass()) {
      case 'EQUIPMENT': {
        const asset = this.asset();
        return asset === null ? null : { elementClass: 'EQUIPMENT', equipmentId: asset.id };
      }
      case 'CHATBOT_SERVICE':
        return { elementClass: 'CHATBOT_SERVICE' };
      case 'SITE_ELEMENT': {
        const reference = this.siteLocationMode() === 'REFERENCE' ? this.asset() : null;
        const site = this.manualSiteName().trim();
        const place =
          reference !== null
            ? { referenceEquipmentId: reference.id }
            : this.siteLocationMode() === 'MANUAL' && site !== ''
              ? { manualSiteName: site }
              : null;
        if (place === null) return null;
        return {
          elementClass: 'SITE_ELEMENT',
          siteElementKind: this.siteKind(),
          elementName: this.siteElementName().trim() || undefined,
          ...place,
        };
      }
    }
  }

  setWholeEquipment(): void {
    this.connectorIds.set([]);
  }

  toggleConnector(id: string): void {
    this.connectorIds.update((ids) =>
      ids.includes(id) ? ids.filter((current) => current !== id) : [...ids, id],
    );
  }

  /** Crea el ticket. Devuelve el detalle, o `null` si no se pudo (el motivo queda en `error`, `matches` o `numberingBlock`). */
  async submit(input: TicketInput): Promise<TicketDetailResponse | null> {
    const missing = this.missingElement();
    if (missing !== '') {
      this.error.set(missing);
      return null;
    }
    this.submitting.set(true);
    this.error.set('');
    this.numberingBlock.set(null);
    try {
      const created = await firstValueFrom(this.api.createTicket(this.request(input)));
      this.clientRequestId = crypto.randomUUID();
      await this.attachInitialEvidence(created);
      return created;
    } catch (error) {
      this.explain(error);
      return null;
    } finally {
      this.submitting.set(false);
    }
  }

  /**
   * Carga la evidencia inicial en el ticket ya creado. Si falla, el ticket queda creado: se guarda el motivo para
   * avisar y la evidencia se adjunta después desde el detalle, en vez de repetir la creación.
   */
  private async attachInitialEvidence(created: TicketDetailResponse): Promise<void> {
    const file = this.evidenceFile();
    this.evidenceWarning.set('');
    if (file === null) return;
    try {
      await firstValueFrom(
        this.api.uploadEvidence(created.summary.id, file, {
          version: created.summary.version,
          purpose: 'INITIAL',
        }),
      );
    } catch (error) {
      this.evidenceWarning.set(toProblem(error).detail);
    } finally {
      this.evidenceFile.set(null);
    }
  }

  /** Lo que falta del elemento para poder crear; vacio si esta completo. El servidor valida lo mismo. */
  private missingElement(): string {
    switch (this.elementClass()) {
      case 'EQUIPMENT':
        return this.asset() === null ? 'Elija el equipo afectado.' : '';
      case 'CHATBOT_SERVICE':
        return '';
      case 'SITE_ELEMENT': {
        if (this.siteKind() === 'OTHER' && this.siteElementName().trim() === '') {
          return 'Escriba el nombre del elemento del sitio.';
        }
        if (this.siteLocationMode() === 'REFERENCE') {
          return this.asset() === null ? 'Elija el equipo de referencia del sitio.' : '';
        }
        return this.manualSiteName().trim() === '' || this.manualLocation().trim() === ''
          ? 'Escriba el nombre del sitio y su ubicación.'
          : '';
      }
    }
  }

  /** La parte de la solicitud que depende de la clase: un chatbot o una barrera nunca llevan equipo ni conectores. */
  private element(): Partial<CreateTicketRequest> {
    switch (this.elementClass()) {
      case 'EQUIPMENT':
        return {
          elementClass: 'EQUIPMENT',
          equipmentId: this.asset()?.id,
          connectorIds: [...this.connectorIds()],
        };
      case 'CHATBOT_SERVICE': {
        const scope = this.chatbotScope();
        return {
          elementClass: 'CHATBOT_SERVICE',
          ...(scope === '' ? {} : { chatbotScope: scope }),
        };
      }
      case 'SITE_ELEMENT': {
        const siteElement: SiteElementRequest = {
          kind: this.siteKind(),
          elementName: this.siteElementName().trim() || null,
          ...(this.siteLocationMode() === 'REFERENCE'
            ? { referenceEquipmentId: this.asset()?.id }
            : {
                manualSiteName: this.manualSiteName().trim(),
                manualLocation: this.manualLocation().trim(),
                manualMapsUrl: this.manualMapsUrl().trim() || null,
              }),
        };
        return { elementClass: 'SITE_ELEMENT', siteElement };
      }
    }
  }

  private request(input: TicketInput): CreateTicketRequest {
    return {
      clientRequestId: this.clientRequestId,
      ...this.element(),
      elementClass: this.elementClass(),
      incidentType: input.incidentType,
      description: input.description.trim(),
      reportOrigin: input.reportOrigin,
      reportOriginDetail: input.reportOriginDetail?.trim() || null,
      priority: input.priority,
      priorityReason: input.priorityReason?.trim() || null,
      t0: input.t0 || null,
      t0Reason: input.t0Reason?.trim() || null,
      duplicateOverrideReason: input.duplicateOverrideReason?.trim() || null,
    };
  }

  private explain(error: unknown): void {
    const problem = toProblem(error);
    switch (problem.code) {
      case 'TICKET_OPEN_DUPLICATE': {
        const open = problem.properties['openTickets'];
        if (Array.isArray(open)) this.matches.set(open as OpenMatchResponse[]);
        this.error.set(problem.detail);
        break;
      }
      case 'TICKET_NUMBERING_NOT_CONFIGURED':
        this.numberingBlock.set('NOT_CONFIGURED');
        this.error.set(problem.detail);
        break;
      case 'TICKET_NUMBERING_ENVIRONMENT_MISMATCH':
        this.numberingBlock.set('ENVIRONMENT_MISMATCH');
        this.error.set(problem.detail);
        break;
      default:
        this.error.set(problem.detail);
    }
  }
}
