import {
  COMMUNICATION_CHANNEL_LABELS,
  COMMUNICATION_KIND_LABELS,
  CommunicationChannel,
  CommunicationKind,
  EVIDENCE_PURPOSE_LABELS,
  EvidencePurpose,
  EventType,
  INCIDENT_TYPE_LABELS,
  IncidentType,
  MILESTONE_STATUS_LABELS,
  MilestoneStatus,
  PRIORITY_LABELS,
  Priority,
  REPORT_ORIGIN_LABELS,
  ReportOrigin,
  VISIT_STAGE_LABELS,
  VisitStage,
  fileSizeText,
} from './incident-labels';
import {
  CAUSE_KIND_LABELS,
  CLOSURE_RESULT_LABELS,
  CauseKind,
  ClosureResult,
} from './incident-closure';
import { formatInstant } from './incident-time';

/** Los datos de un evento tal como llegan del servidor: acotados por tipo, nunca libres desde el cliente. */
export type EventDetails = Readonly<Record<string, unknown>>;

function text(details: EventDetails, key: string): string | undefined {
  const value = details[key];
  return typeof value === 'string' && value !== '' ? value : undefined;
}

function label<K extends string>(
  labels: Readonly<Record<K, string>>,
  value: string | undefined,
): string | undefined {
  if (value === undefined) return undefined;
  return (labels as Readonly<Record<string, string>>)[value] ?? value;
}

const CORRECTED_FIELDS: Readonly<Record<string, string>> = {
  DESCRIPTION: 'Descripción',
  REPORT_ORIGIN: 'Origen del aviso',
};

/**
 * Una línea por dato relevante de un evento para mostrarlo en la cronología: qué hito, qué etapa, qué cambió y desde
 * qué valor. Sin esto, «Hito registrado» o «Visita técnica» no dicen qué pasó. Los tipos que ya explican todo con su
 * texto (observación, acción, validación) no agregan líneas.
 */
export function eventSummary(
  type: EventType,
  details: EventDetails,
  provider?: string | null,
): string[] {
  switch (type) {
    case 'MILESTONE_RECORDED':
    case 'MILESTONE_CORRECTED': {
      const status = label<MilestoneStatus>(MILESTONE_STATUS_LABELS, text(details, 'status'));
      const previous = label<MilestoneStatus>(
        MILESTONE_STATUS_LABELS,
        text(details, 'previousStatus'),
      );
      const before = text(details, 'previousOccurredAt');
      const lines = [`${text(details, 'kind') ?? 'Hito'}: ${status ?? '—'}`];
      if (type === 'MILESTONE_CORRECTED' && previous !== undefined) {
        lines.push(`Antes: ${previous}${before ? ' · ' + formatInstant(before) : ''}`);
      }
      return lines;
    }
    case 'VISIT_STAGE': {
      const stage = label<VisitStage>(VISIT_STAGE_LABELS, text(details, 'stage'));
      const previous = label<VisitStage>(VISIT_STAGE_LABELS, text(details, 'previousStage'));
      const first = `${stage ?? '—'}${provider ? ' · ' + provider : ''}`;
      return previous === undefined ? [first] : [first, `Antes: ${previous}`];
    }
    case 'PRIORITY_CHANGED':
      return [
        `Prioridad: ${label<Priority>(PRIORITY_LABELS, text(details, 'previous')) ?? '—'} → ${
          label<Priority>(PRIORITY_LABELS, text(details, 'new')) ?? '—'
        }`,
      ];
    case 'RESPONSIBLE_ASSIGNED':
      return [`Nuevo responsable: ${text(details, 'newName') ?? '—'}`];
    case 'SHIFT_HANDOVER': {
      const lines: string[] = [];
      const from = text(details, 'outgoingResponsibleName');
      const to = text(details, 'incomingResponsibleName');
      if (from !== undefined || to !== undefined) {
        lines.push(`De ${from ?? '—'} a ${to ?? '—'}`);
      }
      lines.push(`Estado actual: ${text(details, 'currentStatus') ?? '—'}`);
      lines.push(`Pendientes: ${text(details, 'pending') ?? '—'}`);
      const next = text(details, 'nextCheckAt');
      if (next !== undefined) lines.push(`Próxima verificación: ${formatInstant(next)}`);
      lines.push(`Criterio de cierre: ${text(details, 'closureCriterion') ?? '—'}`);
      return lines;
    }
    case 'RECLASSIFIED': {
      const proposed = label<Priority>(PRIORITY_LABELS, text(details, 'proposedPriority'));
      return [
        `Tipo: ${label<IncidentType>(INCIDENT_TYPE_LABELS, text(details, 'previousType')) ?? '—'} → ${
          label<IncidentType>(INCIDENT_TYPE_LABELS, text(details, 'newType')) ?? '—'
        }`,
        ...(proposed ? [`Prioridad propuesta: ${proposed}`] : []),
      ];
    }
    case 'DATA_CORRECTED': {
      const field = text(details, 'field');
      const name = field ? (CORRECTED_FIELDS[field] ?? field) : 'Dato';
      const convert = (value: string | undefined) =>
        field === 'REPORT_ORIGIN' ? label<ReportOrigin>(REPORT_ORIGIN_LABELS, value) : value;
      return [
        `${name}: «${convert(text(details, 'previous')) ?? '—'}» → «${convert(text(details, 'new')) ?? '—'}»`,
      ];
    }
    case 'TICKET_CLOSED': {
      const result = label<ClosureResult>(CLOSURE_RESULT_LABELS, text(details, 'result'));
      const cause = label<CauseKind>(CAUSE_KIND_LABELS, text(details, 'causeKind'));
      const original = text(details, 'duplicateOfCode');
      return [
        `Resultado: ${result ?? '—'}${original ? ' de ' + original : ''}`,
        `Causa: ${cause ?? '—'}`,
      ];
    }
    case 'TICKET_REOPENED': {
      const episode = details['episode'];
      return typeof episode === 'number' ? [`Episodio ${episode}`] : [];
    }
    case 'COMMUNICATION_RECORDED': {
      const channel = label<CommunicationChannel>(
        COMMUNICATION_CHANNEL_LABELS,
        text(details, 'channel'),
      );
      const detail = text(details, 'channelDetail');
      const kind = label<CommunicationKind>(COMMUNICATION_KIND_LABELS, text(details, 'kind'));
      return [`${kind ?? '—'} · ${channel ?? '—'}${detail ? ': ' + detail : ''}`];
    }
    case 'EVIDENCE_ADDED': {
      const size = details['sizeBytes'];
      const purpose = label<EvidencePurpose>(EVIDENCE_PURPOSE_LABELS, text(details, 'purpose'));
      return [
        [
          text(details, 'format'),
          typeof size === 'number' ? fileSizeText(size) : undefined,
          purpose,
        ]
          .filter((part) => part !== undefined)
          .join(' · '),
      ];
    }
    default:
      return [];
  }
}
