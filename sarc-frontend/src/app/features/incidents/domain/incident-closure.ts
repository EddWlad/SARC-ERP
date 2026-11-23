import { ElementClass, MilestoneStatus } from './incident-labels';

/** Resultado de cierre de un episodio (FR-047). Los dos últimos quedan fuera de los indicadores (FR-050). */
export type ClosureResult =
  'RECOVERED' | 'NO_FAULT_CONFIRMED' | 'DUPLICATE' | 'REGISTERED_IN_ERROR';

export const CLOSURE_RESULT_LABELS: Readonly<Record<ClosureResult, string>> = {
  RECOVERED: 'Recuperado',
  NO_FAULT_CONFIRMED: 'Sin falla confirmada',
  DUPLICATE: 'Duplicado de otro ticket',
  REGISTERED_IN_ERROR: 'Registrado por error',
};

/** Un ticket cerrado así no se reabre y se informa aparte de los indicadores (FR-049, FR-050). */
export function isExcludedResult(result: ClosureResult | '' | null | undefined): boolean {
  return result === 'DUPLICATE' || result === 'REGISTERED_IN_ERROR';
}

export type CauseKind = 'CONFIRMED' | 'PROBABLE' | 'UNDETERMINED';

export const CAUSE_KIND_LABELS: Readonly<Record<CauseKind, string>> = {
  CONFIRMED: 'Confirmada',
  PROBABLE: 'Probable',
  UNDETERMINED: 'No determinada',
};

export type ClosureCheck = 'CONFIRMED' | 'NOT_APPLICABLE';

export const CLOSURE_CHECK_LABELS: Readonly<Record<ClosureCheck, string>> = {
  CONFIRMED: 'Confirmada',
  NOT_APPLICABLE: 'No aplica',
};

/** Las cuatro confirmaciones del protocolo para un equipo (FR-047), en el orden en que se muestran. */
export const CLOSURE_CHECK_KEYS = [
  'platformNormal',
  'stabilityChecked',
  'noAffectedUser',
  'postTest',
] as const;

export type ClosureCheckKey = (typeof CLOSURE_CHECK_KEYS)[number];

export const CLOSURE_CHECK_QUESTIONS: Readonly<Record<ClosureCheckKey, string>> = {
  platformNormal: 'Estado normal en la plataforma principal',
  stabilityChecked: 'Estabilidad comprobada, cuando aplique',
  noAffectedUser: 'Ningún usuario afectado pendiente ni sesión sin control',
  postTest: 'Prueba posterior realizada o no aplicable',
};

/**
 * Los mismos cuatro puntos como etiqueta de un campo del diálogo de cierre: cortos para que la etiqueta flotante quepa
 * a 360 px sin recortarse. El texto completo del protocolo va en `CLOSURE_CHECK_QUESTIONS`, en el detalle del ticket.
 */
export const CLOSURE_CHECK_FIELD_LABELS: Readonly<Record<ClosureCheckKey, string>> = {
  platformNormal: 'Plataforma en estado normal',
  stabilityChecked: 'Estabilidad comprobada',
  noAffectedUser: 'Sin usuarios ni sesiones pendientes',
  postTest: 'Prueba posterior',
};

/** Lo que falta para cerrar, con el código estable del servidor (`TICKET_CLOSE_REQUIREMENTS`). */
export type ClosureMissing =
  | 'T4'
  | 'T1'
  | 'T2'
  | 'T3'
  | 'RESULT'
  | 'DUPLICATE_OF'
  | 'CAUSE'
  | 'CAUSE_TEXT'
  | 'PROBABLE_CAUSE_EVIDENCE'
  | 'CLOSURE_CHECKS'
  | 'FINAL_EVIDENCE'
  | 'CLOSURE_COMMUNICATION'
  | 'DEVIATION_JUSTIFICATION';

export const CLOSURE_MISSING_LABELS: Readonly<Record<ClosureMissing, string>> = {
  T4: 'La hora de cierre (T4)',
  T1: 'T1: registre la primera validación o márquela «No aplica» o «Sin dato»',
  T2: 'T2: registre la primera acción o márquela «No aplica» o «Sin dato»',
  T3: 'T3: registre la recuperación o márquela «No aplica» o «Sin dato»',
  RESULT: 'El resultado del cierre',
  DUPLICATE_OF: 'El ticket original del duplicado',
  CAUSE: 'La causa',
  CAUSE_TEXT: 'La descripción de la causa',
  PROBABLE_CAUSE_EVIDENCE: 'Una evidencia del episodio que respalde la causa probable',
  CLOSURE_CHECKS: 'Las cuatro confirmaciones del protocolo',
  FINAL_EVIDENCE: 'Una evidencia final, o la justificación de su ausencia',
  CLOSURE_COMMUNICATION: 'La comunicación de cierre registrada, o la justificación de su ausencia',
  DEVIATION_JUSTIFICATION: 'La justificación de la desviación frente a la referencia',
};

/** Lo que el diálogo sabe para calcular lo que falta; es el mismo cálculo que hace el servidor. */
export interface ClosureInput {
  readonly elementClass: ElementClass;
  readonly hasT4: boolean;
  readonly milestones: Readonly<Record<'T1' | 'T2' | 'T3', MilestoneStatus>>;
  readonly result: ClosureResult | '';
  readonly duplicateOfId: string;
  readonly causeKind: CauseKind | '';
  readonly causeText: string;
  readonly checks: Readonly<Partial<Record<ClosureCheckKey, ClosureCheck | ''>>>;
  readonly hasFinalEvidence: boolean;
  readonly finalEvidenceWaiver: string;
  readonly hasEpisodeEvidence: boolean;
  readonly hasClosureCommunication: boolean;
  readonly closureCommunicationWaiver: string;
  readonly outsideReference: boolean;
  readonly deviationJustification: string;
}

const blank = (text: string): boolean => text.trim() === '';

/**
 * Los requisitos de cierre que faltan (FR-047, FR-048, FR-048a), en el orden del contrato. Un duplicado o un ticket
 * registrado por error solo necesita T4, el resultado, el original de un duplicado y la causa. Sirve para avisar antes de
 * enviar; la decisión es siempre del servidor.
 */
export function closureMissing(input: ClosureInput): ClosureMissing[] {
  const missing: ClosureMissing[] = [];
  if (!input.hasT4) missing.push('T4');
  const excluded = isExcludedResult(input.result);
  if (!excluded) {
    for (const kind of ['T1', 'T2', 'T3'] as const) {
      if (input.milestones[kind] === 'PENDING') missing.push(kind);
    }
  }
  if (input.result === '') missing.push('RESULT');
  else if (input.result === 'DUPLICATE' && input.duplicateOfId === '') missing.push('DUPLICATE_OF');
  if (input.causeKind === '') {
    missing.push('CAUSE');
  } else {
    if (input.causeKind !== 'UNDETERMINED' && blank(input.causeText)) missing.push('CAUSE_TEXT');
    if (input.causeKind === 'PROBABLE' && !input.hasEpisodeEvidence) {
      missing.push('PROBABLE_CAUSE_EVIDENCE');
    }
  }
  if (excluded) return missing;
  if (
    input.elementClass === 'EQUIPMENT' &&
    !CLOSURE_CHECK_KEYS.every((key) => (input.checks[key] ?? '') !== '')
  ) {
    missing.push('CLOSURE_CHECKS');
  }
  if (!input.hasFinalEvidence && blank(input.finalEvidenceWaiver)) missing.push('FINAL_EVIDENCE');
  if (!input.hasClosureCommunication && blank(input.closureCommunicationWaiver)) {
    missing.push('CLOSURE_COMMUNICATION');
  }
  if (input.outsideReference && blank(input.deviationJustification)) {
    missing.push('DEVIATION_JUSTIFICATION');
  }
  return missing;
}
