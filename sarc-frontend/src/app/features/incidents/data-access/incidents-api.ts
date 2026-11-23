import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { MessageOptions } from '../domain/incident-message';
import {
  AddRelationRequest,
  AnnulRequest,
  ConfigureNumberingRequest,
  CloseRequest,
  CommunicationRequest,
  CorrectDataRequest,
  CreateTicketRequest,
  EligibleResponsibleResponse,
  EventRequest,
  EvidenceMeta,
  HandoverRequest,
  IncidentAssetResponse,
  IncidentSummaryResponse,
  MessageResponse,
  MilestoneRequest,
  NumberingResponse,
  OpenMatchResponse,
  PriorityRequest,
  ReopenRequest,
  ReclassifyRequest,
  ResponsibleRequest,
  TicketDetailResponse,
  TicketPageResponse,
  VisitRequest,
  VisitStageRequest,
} from '../../../core/api/generated';
import { API_BASE } from '../../../core/http/api';
import { ReportFormat } from '../../../shared/reports/report-format';

/** Filtros, orden y pagina del listado de tickets (FR-061). Los vacios no se envian. */
export interface TicketQuery {
  readonly state?: string;
  readonly elementClass?: string;
  readonly incidentType?: string;
  readonly priority?: string;
  readonly network?: string;
  readonly condition?: string;
  readonly search?: string;
  /** Dias inclusivos del periodo; con ellos la lista trae `stateAtPeriodEnd` y filtra el estado al final (FR-074). */
  readonly from?: string;
  readonly to?: string;
  /** `campo` o `campo,asc|desc`. */
  readonly sort?: string;
  readonly page?: number;
  readonly size?: number;
}

/** Periodo y filtros del informe «Control de tickets»; sin `from` y `to` es todo el historial (FR-072). */
export interface ControlReportQuery {
  readonly from?: string;
  readonly to?: string;
  readonly state?: string;
  readonly network?: string;
}

/** Periodo (dias inclusivos) y filtros del tablero y del reporte de gestion (FR-080, FR-084). */
export interface ManagementQuery {
  readonly from: string;
  readonly to: string;
  readonly network?: string;
  readonly elementClass?: string;
  readonly incidentType?: string;
  readonly priority?: string;
}

/** Elemento sobre el que se busca un aviso de duplicado (FR-016). */
export interface OpenMatchQuery {
  readonly elementClass: string;
  readonly equipmentId?: string;
  readonly siteElementKind?: string;
  readonly referenceEquipmentId?: string;
  readonly manualSiteName?: string;
  readonly elementName?: string;
}

function params(values: object): Record<string, string | number> {
  const result: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value as string | number;
    }
  }
  return result;
}

/** Acceso HTTP a `/api/v1/incidents`. Cada historia agrega aqui sus llamadas. */
@Service()
export class IncidentsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/incidents`;

  /** Estado de la numeracion; lo lee cualquier rol autorizado. */
  numbering() {
    return this.http.get<NumberingResponse>(`${this.baseUrl}/numbering`);
  }

  /** Configura el numero inicial; solo un Administrador, y solo antes del primer ticket. */
  configureNumbering(body: ConfigureNumberingRequest) {
    return this.http.put<NumberingResponse>(`${this.baseUrl}/numbering`, body);
  }

  tickets(query: TicketQuery) {
    return this.http.get<TicketPageResponse>(`${this.baseUrl}/tickets`, { params: params(query) });
  }

  /** «Control de tickets» en PDF o Excel, con las mismas filas que el listado del mismo periodo (FR-077). */
  downloadControlReport(format: ReportFormat, query: ControlReportQuery) {
    return this.http.get(`${this.baseUrl}/reports/control`, {
      params: { ...params(query), format },
      observe: 'response',
      responseType: 'blob',
    });
  }

  /** Cifras del tablero de gestion: las mismas del PDF y el Excel del mismo corte (FR-086). */
  summary(query: ManagementQuery) {
    return this.http.get<IncidentSummaryResponse>(`${this.baseUrl}/summary`, {
      params: params(query),
    });
  }

  /** «Gestion de incidencias» en PDF o Excel, con los filtros del tablero. */
  downloadManagementReport(format: ReportFormat, query: ManagementQuery) {
    return this.http.get(`${this.baseUrl}/reports/management`, {
      params: { ...params(query), format },
      observe: 'response',
      responseType: 'blob',
    });
  }

  ticket(id: string) {
    return this.http.get<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}`);
  }

  createTicket(body: CreateTicketRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets`, body);
  }

  /** Tickets abiertos del mismo elemento, para avisar antes de crear otro. Solo editores. */
  openMatches(query: OpenMatchQuery) {
    return this.http.get<OpenMatchResponse[]>(`${this.baseUrl}/tickets/open-matches`, {
      params: params(query),
    });
  }

  /** Equipos activos para elegir el elemento afectado. Solo editores. */
  assets(search: string, limit = 20) {
    return this.http.get<IncidentAssetResponse[]>(`${this.baseUrl}/assets`, {
      params: params({ search, limit }),
    });
  }

  addRelation(id: string, body: AddRelationRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/relations`, body);
  }

  removeRelation(id: string, relatedId: string, version: number) {
    return this.http.delete<TicketDetailResponse>(
      `${this.baseUrl}/tickets/${id}/relations/${relatedId}`,
      { params: { version } },
    );
  }

  // ----- Seguimiento (US2): cada escritura lleva la version que leyo el cliente y devuelve el detalle -----

  recordMilestone(id: string, ordinal: number, kind: string, body: MilestoneRequest) {
    return this.http.put<TicketDetailResponse>(
      `${this.baseUrl}/tickets/${id}/episodes/${ordinal}/milestones/${kind}`,
      body,
    );
  }

  addEvent(id: string, body: EventRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/events`, body);
  }

  annulEvent(id: string, eventId: string, body: AnnulRequest) {
    return this.http.post<TicketDetailResponse>(
      `${this.baseUrl}/tickets/${id}/events/${eventId}/annul`,
      body,
    );
  }

  changePriority(id: string, body: PriorityRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/priority`, body);
  }

  assignResponsible(id: string, body: ResponsibleRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/responsible`, body);
  }

  handover(id: string, body: HandoverRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/handover`, body);
  }

  reclassify(id: string, body: ReclassifyRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/reclassify`, body);
  }

  correctData(id: string, body: CorrectDataRequest) {
    return this.http.patch<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}`, body);
  }

  requestVisit(id: string, body: VisitRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/visits`, body);
  }

  moveVisitStage(id: string, visitId: string, body: VisitStageRequest) {
    return this.http.post<TicketDetailResponse>(
      `${this.baseUrl}/tickets/${id}/visits/${visitId}/stage`,
      body,
    );
  }

  /** Cierra el ticket (FR-047). Si faltan requisitos responde 422 con la lista completa en `missing`. */
  closeTicket(id: string, body: CloseRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/close`, body);
  }

  /** Reabre el ticket con un episodio nuevo (FR-049). */
  reopenTicket(id: string, body: ReopenRequest) {
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/reopen`, body);
  }

  /** Registra el envio real de un aviso publicado a mano (FR-058). Copiar el mensaje no llama a esto. */
  recordCommunication(id: string, body: CommunicationRequest) {
    return this.http.post<TicketDetailResponse>(
      `${this.baseUrl}/tickets/${id}/communications`,
      body,
    );
  }

  /** El texto del mensaje para WhatsApp. Solo lectura: no crea eventos ni comunicaciones (FR-059). */
  ticketMessage(id: string, options: MessageOptions) {
    return this.http.get<MessageResponse>(`${this.baseUrl}/tickets/${id}/message`, {
      params: {
        includeDetection: options.includeDetection,
        includeActions: options.includeActions,
      },
    });
  }

  /** Editores activos que pueden ser responsable o recibir un relevo; sin correo ni roles. */
  responsibles() {
    return this.http.get<EligibleResponsibleResponse[]>(`${this.baseUrl}/responsibles`);
  }

  /** Proveedores ya usados en visitas, para sugerirlos al escribir (P-08). */
  providers(search: string) {
    return this.http.get<string[]>(`${this.baseUrl}/providers`, { params: params({ search }) });
  }

  /** Carga una evidencia (imagen o PDF): partes `file` y `meta` (FR-037). */
  uploadEvidence(id: string, file: File, meta: EvidenceMeta) {
    const body = new FormData();
    body.append('file', file, file.name);
    body.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
    return this.http.post<TicketDetailResponse>(`${this.baseUrl}/tickets/${id}/evidence`, body);
  }

  /** Dirección de la descarga de una evidencia: el servidor responde con una redirección a una URL firmada. */
  evidenceUrl(id: string, evidenceId: string): string {
    return `${this.baseUrl}/tickets/${id}/evidence/${evidenceId}`;
  }
}
