import {
  suggestedCommunicationKind,
  COMMUNICATION_CHANNEL_LABELS,
  ELEMENT_CLASS_LABELS,
  INCIDENT_TYPE_LABELS,
  IncidentType,
  milestoneMark,
  MILESTONE_STATUS_LABELS,
  networkLabel,
  NO_NETWORK_LABEL,
  NUMBERING_ENVIRONMENT_LABELS,
  PRIORITY_LABELS,
  REPORT_ORIGIN_LABELS,
  SITE_ELEMENT_KIND_LABELS,
  ticketCode,
  TICKET_STATE_LABELS,
} from './incident-labels';

describe('incident-labels', () => {
  it('cada tipo del catalogo tiene una etiqueta distinta', () => {
    const labels = Object.values(INCIDENT_TYPE_LABELS);
    expect(labels).toHaveLength(12);
    expect(new Set(labels).size).toBe(labels.length);
    expect(INCIDENT_TYPE_LABELS['WAIT_FOR_CONNECTION' satisfies IncidentType]).toBe(
      'Espera de conexión',
    );
    expect(INCIDENT_TYPE_LABELS.UNCATALOGED).toBe('Incidencia no catalogada');
  });

  it('solo hay dos estados principales: Abierto y Cerrado', () => {
    expect(TICKET_STATE_LABELS).toEqual({ OPEN: 'Abierto', CLOSED: 'Cerrado' });
  });

  it('nombra las clases de elemento, de elemento del sitio, prioridad y origen', () => {
    expect(ELEMENT_CLASS_LABELS.CHATBOT_SERVICE).toBe('Servicio de chatbot');
    expect(SITE_ELEMENT_KIND_LABELS.VEHICLE_BARRIER).toBe('Barrera vehicular');
    expect(SITE_ELEMENT_KIND_LABELS.OTHER).toBe('Otro elemento del sitio');
    expect(PRIORITY_LABELS).toEqual({ MEDIUM: 'Media', HIGH: 'Alta', CRITICAL: 'Crítica' });
    expect(REPORT_ORIGIN_LABELS.PLATFORM).toBe('Plataforma de monitoreo');
    expect(REPORT_ORIGIN_LABELS.OTHER).toBe('Otro');
  });

  it('las marcas de hito siguen la especificacion', () => {
    expect(milestoneMark('RECORDED', false)).toBe('R');
    expect(milestoneMark('RECORDED', true)).toBe('R*');
    expect(milestoneMark('NOT_APPLICABLE', false)).toBe('N/A');
    expect(milestoneMark('NO_DATA', false)).toBe('S/D');
    expect(milestoneMark('PENDING', false)).toBe('—');
    expect(MILESTONE_STATUS_LABELS.NO_DATA).toBe('Sin dato');
  });

  it('un ticket sin red se agrupa como «Sin red»', () => {
    expect(networkLabel(null)).toBe(NO_NETWORK_LABEL);
    expect(networkLabel(undefined)).toBe('Sin red');
    expect(networkLabel('')).toBe('Sin red');
    expect(networkLabel('KIA')).toBe('KIA');
  });

  it('el codigo lleva al menos tres digitos y nunca se recorta', () => {
    expect(ticketCode(7)).toBe('TICKET-007');
    expect(ticketCode(86)).toBe('TICKET-086');
    expect(ticketCode(999)).toBe('TICKET-999');
    expect(ticketCode(1000)).toBe('TICKET-1000');
    expect(ticketCode(9001)).toBe('TICKET-9001');
  });

  it('los ambientes de numeracion estan en espanol', () => {
    expect(NUMBERING_ENVIRONMENT_LABELS).toEqual({ PRODUCTION: 'Producción', TEST: 'Prueba' });
  });

  it('el tipo de envio propuesto: cierre si esta cerrado, inicial si no hay avisos y actualizacion si ya hubo uno', () => {
    expect(suggestedCommunicationKind('CLOSED', [])).toBe('CLOSURE');
    expect(suggestedCommunicationKind('OPEN', [])).toBe('INITIAL');
    expect(suggestedCommunicationKind('OPEN', [{ annulled: false }])).toBe('UPDATE');
    expect(suggestedCommunicationKind('OPEN', [{ annulled: true }])).toBe('INITIAL');
  });

  it('los canales de comunicacion estan en español', () => {
    expect(COMMUNICATION_CHANNEL_LABELS.REDAE_INPSERCOM).toBe('Grupo REDAE / INPSERCOM');
    expect(COMMUNICATION_CHANNEL_LABELS.OTHER).toBe('Otro');
  });
});
