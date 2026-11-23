import { inject, Service, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import {
  AnnulRequest,
  CloseRequest,
  CommunicationRequest,
  MessageResponse,
  CorrectDataRequest,
  EligibleResponsibleResponse,
  EventRequest,
  EvidenceMeta,
  HandoverRequest,
  MilestoneRequest,
  PriorityRequest,
  ReopenRequest,
  TicketSummaryResponse,
  ReclassifyRequest,
  TicketDetailResponse,
  VisitRequest,
  VisitStageRequest,
} from '../../../core/api/generated';
import { refreshWhileVisible, RefreshStatus } from '../../../core/http/auto-refresh';
import { toProblem } from '../../../core/http/problem';
import { IncidentsApi } from '../data-access/incidents-api';

/** Cuerpo de una escritura sin la version: la fachada agrega la que leyo el cliente (bloqueo optimista). */
type WithoutVersion<T> = Omit<T, 'version'>;

/** Codigo del 409 cuando otro usuario guardo el ticket despues de que se cargo el detalle. */
const VERSION_CONFLICT = 'TICKET_VERSION_CONFLICT';

/**
 * Detalle de un ticket y su seguimiento. Se actualiza solo mientras la pantalla esta a la vista (FR-063): si una
 * actualizacion falla, conserva el detalle que ya mostraba y avisa. Cada escritura envia la version del detalle y
 * reemplaza el detalle con la respuesta; si otro usuario guardo antes, ofrece recargar en vez de sobrescribir.
 */
@Service({ autoProvided: false })
export class TicketDetailFacade {
  private readonly api = inject(IncidentsApi);
  private ticket = 0;
  private id = '';

  readonly detail = signal<TicketDetailResponse | undefined>(undefined);
  readonly loading = signal(false);
  /** No hay detalle que mostrar: no cargo nunca, o el ticket no existe. */
  readonly failed = signal(false);
  readonly notFound = signal(false);
  readonly refreshFailed = signal(false);
  readonly errorMessage = signal('');
  /** Otro usuario modifico el ticket: la pantalla ofrece recargar antes de seguir. */
  readonly conflict = signal(false);
  readonly refresh = new RefreshStatus();

  constructor() {
    refreshWhileVisible(() => void this.reload());
  }

  /** Muestra otro ticket y lo carga. */
  async open(id: string): Promise<void> {
    this.id = id;
    this.detail.set(undefined);
    this.failed.set(false);
    this.notFound.set(false);
    this.conflict.set(false);
    await this.reload();
  }

  async reload(): Promise<void> {
    if (this.id === '') return;
    const ticket = ++this.ticket;
    this.loading.set(true);
    try {
      const detail = await firstValueFrom(this.api.ticket(this.id));
      if (ticket !== this.ticket) return;
      this.detail.set(detail);
      this.failed.set(false);
      this.refreshFailed.set(false);
      this.conflict.set(false);
      this.errorMessage.set('');
      this.refresh.markSuccess();
    } catch (error) {
      if (ticket !== this.ticket) return;
      const problem = toProblem(error);
      this.errorMessage.set(problem.detail);
      this.refresh.markFailure(error);
      if (this.detail() !== undefined) {
        this.refreshFailed.set(true);
      } else {
        this.failed.set(true);
        this.notFound.set(problem.status === 404);
      }
    } finally {
      if (ticket === this.ticket) this.loading.set(false);
    }
  }

  // ----- Seguimiento. Cada metodo lanza el error del servidor para que el dialogo lo muestre en su lugar. -----

  recordMilestone(ordinal: number, kind: string, body: WithoutVersion<MilestoneRequest>) {
    return this.write((id, version) =>
      this.api.recordMilestone(id, ordinal, kind, { ...body, version }),
    );
  }

  addEvent(body: WithoutVersion<EventRequest>) {
    return this.write((id, version) => this.api.addEvent(id, { ...body, version }));
  }

  annulEvent(eventId: string, body: WithoutVersion<AnnulRequest>) {
    return this.write((id, version) => this.api.annulEvent(id, eventId, { ...body, version }));
  }

  changePriority(body: WithoutVersion<PriorityRequest>) {
    return this.write((id, version) => this.api.changePriority(id, { ...body, version }));
  }

  assignResponsible(responsibleId: string) {
    return this.write((id, version) => this.api.assignResponsible(id, { responsibleId, version }));
  }

  handover(body: WithoutVersion<HandoverRequest>) {
    return this.write((id, version) => this.api.handover(id, { ...body, version }));
  }

  reclassify(body: WithoutVersion<ReclassifyRequest>) {
    return this.write((id, version) => this.api.reclassify(id, { ...body, version }));
  }

  correctData(body: WithoutVersion<CorrectDataRequest>) {
    return this.write((id, version) => this.api.correctData(id, { ...body, version }));
  }

  requestVisit(body: WithoutVersion<VisitRequest>) {
    return this.write((id, version) => this.api.requestVisit(id, { ...body, version }));
  }

  moveVisitStage(visitId: string, body: WithoutVersion<VisitStageRequest>) {
    return this.write((id, version) => this.api.moveVisitStage(id, visitId, { ...body, version }));
  }

  /**
   * Registra el envío real de un aviso y, si se eligió, carga la evidencia ligada a él. El envío ya registrado no se
   * deshace si la evidencia falla: se devuelve el motivo para avisarlo, en vez de pedir repetir el envío y duplicarlo.
   */
  async recordCommunication(
    body: WithoutVersion<CommunicationRequest>,
    evidence: File | null,
  ): Promise<string | null> {
    await this.write((id, version) => this.api.recordCommunication(id, { ...body, version }));
    if (evidence === null) return null;
    const latest = (this.detail()?.communications ?? []).reduce<
      { id: string; recordedAt: string } | undefined
    >(
      (found, item) =>
        !found || Date.parse(item.recordedAt) > Date.parse(found.recordedAt) ? item : found,
      undefined,
    );
    if (latest === undefined) return null;
    try {
      await this.uploadEvidence(evidence, { purpose: 'GENERAL', communicationId: latest.id });
      return null;
    } catch (error) {
      return toProblem(error).detail;
    }
  }

  /** Cierra el ticket. Si faltan requisitos el servidor responde 422 con `missing`; el detalle no cambia. */
  closeTicket(body: WithoutVersion<CloseRequest>) {
    return this.write((id, version) => this.api.closeTicket(id, { ...body, version }));
  }

  reopenTicket(body: WithoutVersion<ReopenRequest>) {
    return this.write((id, version) => this.api.reopenTicket(id, { ...body, version }));
  }

  /** Tickets que coinciden con el texto, para elegir el original de un duplicado. Solo lectura. */
  async searchTickets(text: string): Promise<TicketSummaryResponse[]> {
    const page = await firstValueFrom(this.api.tickets({ search: text.trim(), size: 8 }));
    return page.items.filter((item) => item.id !== this.id);
  }

  /** El mensaje para WhatsApp del ticket abierto; solo lectura. */
  message(options: {
    includeDetection: boolean;
    includeActions: boolean;
  }): Promise<MessageResponse> {
    return firstValueFrom(this.api.ticketMessage(this.id, options));
  }

  uploadEvidence(file: File, meta: WithoutVersion<EvidenceMeta>) {
    return this.write((id, version) => this.api.uploadEvidence(id, file, { ...meta, version }));
  }

  /** Editores activos para elegir responsable o entrante de un relevo. */
  responsibles(): Promise<EligibleResponsibleResponse[]> {
    return firstValueFrom(this.api.responsibles());
  }

  /** Proveedores ya usados en visitas que contienen el texto. */
  providers(search: string): Promise<string[]> {
    return firstValueFrom(this.api.providers(search));
  }

  /** Direccion de descarga de una evidencia del ticket abierto. */
  evidenceUrl(evidenceId: string): string {
    return this.api.evidenceUrl(this.id, evidenceId);
  }

  private async write(
    call: (id: string, version: number) => Observable<TicketDetailResponse>,
  ): Promise<void> {
    const current = this.detail();
    if (current === undefined) {
      throw new Error('No hay un ticket cargado');
    }
    // Una recarga ya enviada traería la versión anterior y pisaría esta escritura: se invalida.
    this.ticket++;
    try {
      this.detail.set(await firstValueFrom(call(this.id, current.summary.version)));
      this.conflict.set(false);
      this.refreshFailed.set(false);
    } catch (error) {
      if (toProblem(error).code === VERSION_CONFLICT) {
        this.conflict.set(true);
      }
      throw error;
    }
  }
}
