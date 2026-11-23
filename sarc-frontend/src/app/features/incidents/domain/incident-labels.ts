/**
 * Etiquetas en español de incidencias (feature 010). Repiten las de `IncidentLabels` del backend, que es la fuente:
 * si se agrega un valor allá, se agrega aquí con su etiqueta.
 */

export type IncidentType =
  | 'WAIT_FOR_CONNECTION'
  | 'DISCONNECTION'
  | 'CONNECTOR_FAILURE'
  | 'EMERGENCY_STOP'
  | 'CONNECTOR_STUCK'
  | 'CANNOT_STOP_CHARGING'
  | 'KIA_PERSISTENT_OUTAGE'
  | 'INTERMITTENCY'
  | 'INSTALLATION_OR_CONNECTIVITY'
  | 'CHATBOT_SERVICE_FAILURE'
  | 'SITE_ELEMENT_FAILURE'
  | 'UNCATALOGED';

export const INCIDENT_TYPE_LABELS: Readonly<Record<IncidentType, string>> = {
  WAIT_FOR_CONNECTION: 'Espera de conexión',
  DISCONNECTION: 'Desconexión',
  CONNECTOR_FAILURE: 'Falla de conector',
  EMERGENCY_STOP: 'Paro de emergencia',
  CONNECTOR_STUCK: 'Conector retenido en el vehículo',
  CANNOT_STOP_CHARGING: 'No permite detener la carga',
  KIA_PERSISTENT_OUTAGE: 'Punto KIA con caída persistente',
  INTERMITTENCY: 'Intermitencia',
  INSTALLATION_OR_CONNECTIVITY: 'Novedad de instalación o conectividad',
  CHATBOT_SERVICE_FAILURE: 'Falla del servicio de chatbot',
  SITE_ELEMENT_FAILURE: 'Falla de elemento del sitio',
  UNCATALOGED: 'Incidencia no catalogada',
};

/** Tipos que sirven para un equipo o sus conectores (FR-022): todos menos los del chatbot y los del sitio. */
export const EQUIPMENT_INCIDENT_TYPES: readonly IncidentType[] = [
  'WAIT_FOR_CONNECTION',
  'DISCONNECTION',
  'CONNECTOR_FAILURE',
  'EMERGENCY_STOP',
  'CONNECTOR_STUCK',
  'CANNOT_STOP_CHARGING',
  'KIA_PERSISTENT_OUTAGE',
  'INTERMITTENCY',
  'INSTALLATION_OR_CONNECTIVITY',
  'UNCATALOGED',
];

export type ElementClass = 'EQUIPMENT' | 'CHATBOT_SERVICE' | 'SITE_ELEMENT';

export const ELEMENT_CLASS_LABELS: Readonly<Record<ElementClass, string>> = {
  EQUIPMENT: 'Equipo',
  CHATBOT_SERVICE: 'Servicio de chatbot',
  SITE_ELEMENT: 'Elemento del sitio',
};

export type SiteElementKind = 'VEHICLE_BARRIER' | 'OTHER';

export const SITE_ELEMENT_KIND_LABELS: Readonly<Record<SiteElementKind, string>> = {
  VEHICLE_BARRIER: 'Barrera vehicular',
  OTHER: 'Otro elemento del sitio',
};

/** Alcance de una falla del servicio de chatbot; es opcional al registrarla (FR-005). */
export type ChatbotScope = 'TOTAL' | 'PARTIAL';

export const CHATBOT_SCOPE_LABELS: Readonly<Record<ChatbotScope, string>> = {
  TOTAL: 'Total',
  PARTIAL: 'Parcial',
};

/**
 * Tipos de incidencia que admite cada clase de elemento (FR-022). Un equipo admite todos menos los del chatbot y los
 * del sitio; el chatbot y los elementos del sitio solo su tipo propio o «Incidencia no catalogada».
 */
export const INCIDENT_TYPES_BY_CLASS: Readonly<Record<ElementClass, readonly IncidentType[]>> = {
  EQUIPMENT: EQUIPMENT_INCIDENT_TYPES,
  CHATBOT_SERVICE: ['CHATBOT_SERVICE_FAILURE', 'UNCATALOGED'],
  SITE_ELEMENT: ['SITE_ELEMENT_FAILURE', 'UNCATALOGED'],
};

export type Priority = 'MEDIUM' | 'HIGH' | 'CRITICAL';

export const PRIORITY_LABELS: Readonly<Record<Priority, string>> = {
  MEDIUM: 'Media',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
};

export type ReportOrigin =
  'PLATFORM' | 'CHATBOT' | 'SITE_STAFF' | 'CUSTOMER' | 'PROVIDER' | 'OTHER';

export const REPORT_ORIGIN_LABELS: Readonly<Record<ReportOrigin, string>> = {
  PLATFORM: 'Plataforma de monitoreo',
  CHATBOT: 'Chatbot',
  SITE_STAFF: 'Personal en sitio o seguridad',
  CUSTOMER: 'Cliente',
  PROVIDER: 'Técnico o proveedor',
  OTHER: 'Otro',
};

/** Los únicos estados principales de un ticket (FR-030). */
export type TicketState = 'OPEN' | 'CLOSED';

export const TICKET_STATE_LABELS: Readonly<Record<TicketState, string>> = {
  OPEN: 'Abierto',
  CLOSED: 'Cerrado',
};

export type MilestoneStatus = 'PENDING' | 'RECORDED' | 'NOT_APPLICABLE' | 'NO_DATA';

export const MILESTONE_STATUS_LABELS: Readonly<Record<MilestoneStatus, string>> = {
  PENDING: 'Pendiente',
  RECORDED: 'Registrado',
  NOT_APPLICABLE: 'No aplica',
  NO_DATA: 'Sin dato',
};

/** Grupo de reparto de los tickets sin red: chatbot y elementos del sitio (P-10, P-14). */
export const NO_NETWORK_LABEL = 'Sin red';

/** Marcas de hito (FR-042): R registrado, R* retroactivo, N/A, S/D y — pendiente. */
export function milestoneMark(status: MilestoneStatus, retroactive: boolean): string {
  switch (status) {
    case 'RECORDED':
      return retroactive ? 'R*' : 'R';
    case 'NOT_APPLICABLE':
      return 'N/A';
    case 'NO_DATA':
      return 'S/D';
    case 'PENDING':
      return '—';
  }
}

/** La red conservada en T0; nula es «Sin red». */
export function networkLabel(network: string | null | undefined): string {
  return network ? network : NO_NETWORK_LABEL;
}

/** Ambientes de la numeración (FR-013, P-01). */
export type NumberingEnvironment = 'PRODUCTION' | 'TEST';

export const NUMBERING_ENVIRONMENT_LABELS: Readonly<Record<NumberingEnvironment, string>> = {
  PRODUCTION: 'Producción',
  TEST: 'Prueba',
};

/** Código de un ticket con al menos tres dígitos, sin recortar los más largos (FR-012): TICKET-086, TICKET-1000. */
export function ticketCode(number: number): string {
  return `TICKET-${String(number).padStart(3, '0')}`;
}

/** Condicion de un ticket abierto en el tablero (FR-062); se deriva de los hitos y no es un estado. */
export type TicketCondition = 'PENDING_VALIDATION' | 'IN_ATTENTION' | 'RECOVERED_OBSERVING';

export const TICKET_CONDITION_LABELS: Readonly<Record<TicketCondition, string>> = {
  PENDING_VALIDATION: 'Pendiente de validación',
  IN_ATTENTION: 'En atención',
  RECOVERED_OBSERVING: 'Recuperado en observación',
};

/** Tipo de relacion entre tickets (FR-017). */
export type RelationKind = 'INDEPENDENT_FAILURE' | 'COMMON_FAILURE';

export const RELATION_KIND_LABELS: Readonly<Record<RelationKind, string>> = {
  INDEPENDENT_FAILURE: 'Falla independiente',
  COMMON_FAILURE: 'Falla común',
};

/** Aviso del listado: el codigo buscado es de la secuencia externa anterior y no esta en SARC. */
export const BEFORE_START_MESSAGE =
  'Ese ticket pertenece a la secuencia externa anterior y no está registrado en SARC.';

export type EventType =
  | 'TICKET_CREATED'
  | 'VALIDATION'
  | 'CORRECTIVE_ACTION'
  | 'ESCALATED'
  | 'OBSERVATION'
  | 'DATA_CORRECTED'
  | 'PRIORITY_CHANGED'
  | 'RESPONSIBLE_ASSIGNED'
  | 'SHIFT_HANDOVER'
  | 'RECLASSIFIED'
  | 'VISIT_STAGE'
  | 'COMMUNICATION_RECORDED'
  | 'EVIDENCE_ADDED'
  | 'MILESTONE_RECORDED'
  | 'MILESTONE_CORRECTED'
  | 'TICKET_CLOSED'
  | 'TICKET_REOPENED'
  | 'RELATION_ADDED';

/** Tipos de evento de la cronologia (data-model.md). */
export const EVENT_TYPE_LABELS: Readonly<Record<EventType, string>> = {
  TICKET_CREATED: 'Ticket creado',
  VALIDATION: 'Validación',
  CORRECTIVE_ACTION: 'Acción correctiva',
  ESCALATED: 'Escalamiento',
  OBSERVATION: 'Observación',
  DATA_CORRECTED: 'Dato corregido',
  PRIORITY_CHANGED: 'Cambio de prioridad',
  RESPONSIBLE_ASSIGNED: 'Responsable asignado',
  SHIFT_HANDOVER: 'Relevo de turno',
  RECLASSIFIED: 'Reclasificación',
  VISIT_STAGE: 'Visita técnica',
  COMMUNICATION_RECORDED: 'Comunicación registrada',
  EVIDENCE_ADDED: 'Evidencia agregada',
  MILESTONE_RECORDED: 'Hito registrado',
  MILESTONE_CORRECTED: 'Hito corregido',
  TICKET_CLOSED: 'Ticket cerrado',
  TICKET_REOPENED: 'Ticket reabierto',
  RELATION_ADDED: 'Relación agregada',
};

export type MilestoneKind = 'T0' | 'T1' | 'T2' | 'T3' | 'T4';

/** Los cinco hitos de un episodio (FR-040). T3 y T4 son indicadores separados (P-06). */
export const MILESTONE_KINDS: readonly MilestoneKind[] = ['T0', 'T1', 'T2', 'T3', 'T4'];

export const MILESTONE_KIND_LABELS: Readonly<Record<MilestoneKind, string>> = {
  T0: 'Detección o comunicación',
  T1: 'Primera validación',
  T2: 'Primera solicitud de apoyo o acción',
  T3: 'Recuperación',
  T4: 'Cierre',
};

/** Hitos que se registran con una sola acción desde el detalle (SC-002). T4 se propone dentro del cierre. */
export const QUICK_MILESTONES: readonly MilestoneKind[] = ['T1', 'T2', 'T3'];

/** Pares de hitos con su duración derivada (FR-045). */
export const DURATION_PAIR_LABELS: Readonly<Record<string, string>> = {
  'T1-T0': 'Validación (T1 − T0)',
  'T2-T0': 'Primera acción (T2 − T0)',
  'T3-T0': 'Recuperación (T3 − T0)',
  'T4-T0': 'Cierre (T4 − T0)',
  'T4-T3': 'Observación (T4 − T3)',
};

export type ReferenceMeasure = 'VALIDATION' | 'RECOVERY' | 'ESCALATION';

export const REFERENCE_MEASURE_LABELS: Readonly<Record<ReferenceMeasure, string>> = {
  VALIDATION: 'Validación',
  RECOVERY: 'Recuperación',
  ESCALATION: 'Escalamiento',
};

export type ReferenceResult = 'WITHIN' | 'OUTSIDE' | 'NOT_EVALUABLE' | 'NO_REFERENCE';

/** Resultado frente a una referencia operativa; nunca se presenta como incumplimiento de un SLA contractual. */
export const REFERENCE_RESULT_LABELS: Readonly<Record<ReferenceResult, string>> = {
  WITHIN: 'Dentro de la referencia',
  OUTSIDE: 'Fuera de la referencia',
  NOT_EVALUABLE: 'No evaluable aún',
  NO_REFERENCE: 'Sin referencia',
};

/** Leyenda fija junto a toda referencia de tiempo (FR-051). */
export const REFERENCE_DISCLAIMER = 'Referencia operativa, no SLA contractual';

export type TargetKind = 'TECHNICAL' | 'SUPERVISION' | 'PLATFORM_SUPPORT' | 'OTHER';

/** Destino de un escalamiento, como lista cerrada. Solo el técnico cuenta para la referencia de Espera de conexión. */
export const TARGET_KIND_LABELS: Readonly<Record<TargetKind, string>> = {
  TECHNICAL: 'Técnicos',
  SUPERVISION: 'Supervisión',
  PLATFORM_SUPPORT: 'Soporte de plataforma',
  OTHER: 'Otro',
};

/** Los cuatro tipos de evento que el analista registra a mano; los demás los genera el sistema. */
export type ManualEventType = 'VALIDATION' | 'CORRECTIVE_ACTION' | 'ESCALATED' | 'OBSERVATION';

export const MANUAL_EVENT_TYPES: readonly ManualEventType[] = [
  'VALIDATION',
  'CORRECTIVE_ACTION',
  'ESCALATED',
  'OBSERVATION',
];

/** Eventos que se pueden anular con motivo: los manuales y la carga de una evidencia (FR-036, FR-037). */
export function isAnnullableEvent(type: EventType): boolean {
  return (MANUAL_EVENT_TYPES as readonly string[]).includes(type) || type === 'EVIDENCE_ADDED';
}

export type VisitStage =
  | 'REQUESTED'
  | 'ASSIGNED'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'POSTPONED'
  | 'EXECUTED'
  | 'REPORT_RECEIVED';

export const VISIT_STAGE_LABELS: Readonly<Record<VisitStage, string>> = {
  REQUESTED: 'Solicitada',
  ASSIGNED: 'Asignada',
  CONFIRMED: 'Confirmada',
  CANCELLED: 'Cancelada',
  POSTPONED: 'Aplazada',
  EXECUTED: 'Ejecutada',
  REPORT_RECEIVED: 'Informe recibido',
};

/** Transiciones de una visita (data-model.md); el servidor aplica las mismas. Las finales no tienen siguiente. */
export const VISIT_NEXT_STAGES: Readonly<Record<VisitStage, readonly VisitStage[]>> = {
  REQUESTED: ['ASSIGNED', 'CANCELLED', 'POSTPONED'],
  ASSIGNED: ['CONFIRMED', 'CANCELLED', 'POSTPONED'],
  CONFIRMED: ['EXECUTED', 'CANCELLED', 'POSTPONED'],
  POSTPONED: ['ASSIGNED', 'CONFIRMED'],
  EXECUTED: ['REPORT_RECEIVED'],
  CANCELLED: [],
  REPORT_RECEIVED: [],
};

/** Cancelar o aplazar exige quién decidió, la evidencia y la condición para volver a escalar (FR-035). */
export function visitStageNeedsDecision(stage: VisitStage): boolean {
  return stage === 'CANCELLED' || stage === 'POSTPONED';
}

export type CommunicationChannel =
  | 'REDAE_INPSERCOM'
  | 'KIA_ELECTROLINERAS'
  | 'CARGADORES_ELECTRICOS'
  | 'AUTOEASTERN_ENERLINK'
  | 'DIRECTIVOS'
  | 'EMAIL'
  | 'OTHER';

/** Canales por los que el analista publica el aviso a mano. */
export const COMMUNICATION_CHANNEL_LABELS: Readonly<Record<CommunicationChannel, string>> = {
  REDAE_INPSERCOM: 'Grupo REDAE / INPSERCOM',
  KIA_ELECTROLINERAS: 'Grupo KIA electrolineras',
  CARGADORES_ELECTRICOS: 'Grupo Cargadores eléctricos',
  AUTOEASTERN_ENERLINK: 'Grupo AutoEastern / Enerlink',
  DIRECTIVOS: 'Directivos',
  EMAIL: 'Correo electrónico',
  OTHER: 'Otro',
};

export type CommunicationKind = 'INITIAL' | 'UPDATE' | 'CLOSURE';

export const COMMUNICATION_KIND_LABELS: Readonly<Record<CommunicationKind, string>> = {
  INITIAL: 'Aviso inicial',
  UPDATE: 'Actualización',
  CLOSURE: 'Cierre',
};

/**
 * El tipo que se propone al registrar un envío: el de cierre si el ticket ya se cerró, el inicial si aún no hay ningún
 * aviso no anulado, y una actualización en los demás casos. Solo preselecciona: el analista lo cambia (contracts).
 */
export function suggestedCommunicationKind(
  state: TicketState,
  communications: readonly { readonly annulled: boolean }[],
): CommunicationKind {
  if (state === 'CLOSED') return 'CLOSURE';
  return communications.some((communication) => !communication.annulled) ? 'UPDATE' : 'INITIAL';
}

export type EvidencePurpose = 'GENERAL' | 'INITIAL' | 'FINAL';

export const EVIDENCE_PURPOSE_LABELS: Readonly<Record<EvidencePurpose, string>> = {
  GENERAL: 'General',
  INITIAL: 'Inicial',
  FINAL: 'Final',
};

/** Formatos y tamaño de una evidencia (FR-037); el servidor decide por el contenido real del archivo. */
export const EVIDENCE_ACCEPT = 'image/jpeg,image/png,image/webp,application/pdf';
export const EVIDENCE_MAX_BYTES = 10 * 1024 * 1024;

/** Tamaño legible de un archivo: «850 KB», «2,0 MB». */
export function fileSizeText(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

/**
 * Nota fija del mensaje sobre el aviso mínimo del Protocolo §4.1 (Q3): la plantilla trae los seis datos pedidos y SARC
 * no afirma que el aviso cumpla el protocolo.
 */
export const MESSAGE_PROTOCOL_NOTE =
  'Protocolo §4.1: agrega la fuente de validación, la solicitud concreta y la próxima verificación si el aviso lo requiere.';

/** Recordatorio fijo junto a la carga de evidencias (RF-006 §10.5): las capturas pueden mostrar datos de terceros. */
export const EVIDENCE_PRIVACY_REMINDER =
  'Antes de adjuntar una captura, tape o recorte los datos personales de terceros (nombres, teléfonos, placas, correos) que no hagan falta.';
