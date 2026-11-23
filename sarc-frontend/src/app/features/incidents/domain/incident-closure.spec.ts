import { describe, expect, it } from 'vitest';

import { closureMissing, ClosureInput, isExcludedResult } from './incident-closure';

const COMPLETE: ClosureInput = {
  elementClass: 'EQUIPMENT',
  hasT4: true,
  milestones: { T1: 'RECORDED', T2: 'NOT_APPLICABLE', T3: 'NO_DATA' },
  result: 'RECOVERED',
  duplicateOfId: '',
  causeKind: 'CONFIRMED',
  causeText: 'Falla del enlace',
  checks: {
    platformNormal: 'CONFIRMED',
    stabilityChecked: 'NOT_APPLICABLE',
    noAffectedUser: 'CONFIRMED',
    postTest: 'NOT_APPLICABLE',
  },
  hasFinalEvidence: true,
  finalEvidenceWaiver: '',
  hasEpisodeEvidence: true,
  hasClosureCommunication: true,
  closureCommunicationWaiver: '',
  outsideReference: false,
  deviationJustification: '',
};

const missing = (changes: Partial<ClosureInput>) => closureMissing({ ...COMPLETE, ...changes });

describe('closureMissing', () => {
  it('un cierre completo no tiene nada pendiente, de cualquier clase', () => {
    expect(closureMissing(COMPLETE)).toEqual([]);
    expect(missing({ elementClass: 'CHATBOT_SERVICE', checks: {} })).toEqual([]);
    expect(missing({ elementClass: 'SITE_ELEMENT', checks: {} })).toEqual([]);
  });

  it('T1 a T3 pendientes se piden uno por uno, y cualquier estado explícito vale', () => {
    expect(missing({ milestones: { T1: 'PENDING', T2: 'RECORDED', T3: 'PENDING' } })).toEqual([
      'T1',
      'T3',
    ]);
    expect(missing({ hasT4: false })).toEqual(['T4']);
  });

  it('pide el resultado y la causa, y el original solo de un duplicado', () => {
    expect(missing({ result: '', causeKind: '' })).toEqual(['RESULT', 'CAUSE']);
    expect(missing({ result: 'DUPLICATE' })).toEqual(['DUPLICATE_OF']);
    expect(missing({ result: 'DUPLICATE', duplicateOfId: 'abc' })).toEqual([]);
  });

  it('la causa pide su descripción salvo si no está determinada, y la probable una evidencia', () => {
    expect(missing({ causeText: '  ' })).toEqual(['CAUSE_TEXT']);
    expect(missing({ causeKind: 'UNDETERMINED', causeText: '' })).toEqual([]);
    expect(missing({ causeKind: 'PROBABLE', hasEpisodeEvidence: false })).toEqual([
      'PROBABLE_CAUSE_EVIDENCE',
    ]);
    expect(missing({ causeKind: 'CONFIRMED', hasEpisodeEvidence: false })).toEqual([]);
  });

  it('un equipo pide las cuatro confirmaciones y cada una respondida', () => {
    expect(missing({ checks: {} })).toEqual(['CLOSURE_CHECKS']);
    expect(missing({ checks: { ...COMPLETE.checks, postTest: '' } })).toEqual(['CLOSURE_CHECKS']);
  });

  it('la evidencia final y la comunicación de cierre se reemplazan con una justificación', () => {
    expect(missing({ hasFinalEvidence: false, hasClosureCommunication: false })).toEqual([
      'FINAL_EVIDENCE',
      'CLOSURE_COMMUNICATION',
    ]);
    expect(
      missing({
        hasFinalEvidence: false,
        finalEvidenceWaiver: 'Sin captura',
        hasClosureCommunication: false,
        closureCommunicationWaiver: 'Por llamada',
      }),
    ).toEqual([]);
  });

  it('una desviación pide su justificación', () => {
    expect(missing({ outsideReference: true })).toEqual(['DEVIATION_JUSTIFICATION']);
    expect(
      missing({ outsideReference: true, deviationJustification: 'Esperó al proveedor' }),
    ).toEqual([]);
  });

  it('un duplicado o un registrado por error solo piden T4, resultado, original y causa', () => {
    const bare: Partial<ClosureInput> = {
      milestones: { T1: 'PENDING', T2: 'PENDING', T3: 'PENDING' },
      checks: {},
      hasFinalEvidence: false,
      hasClosureCommunication: false,
      causeKind: 'UNDETERMINED',
      causeText: '',
    };
    expect(missing({ ...bare, result: 'REGISTERED_IN_ERROR' })).toEqual([]);
    expect(missing({ ...bare, result: 'DUPLICATE' })).toEqual(['DUPLICATE_OF']);
    expect(isExcludedResult('DUPLICATE')).toBe(true);
    expect(isExcludedResult('RECOVERED')).toBe(false);
  });

  it('todo lo que falta se informa a la vez y en el orden del contrato', () => {
    expect(
      closureMissing({
        ...COMPLETE,
        hasT4: false,
        milestones: { T1: 'PENDING', T2: 'PENDING', T3: 'PENDING' },
        result: '',
        causeKind: '',
        checks: {},
        hasFinalEvidence: false,
        hasClosureCommunication: false,
        outsideReference: true,
      }),
    ).toEqual([
      'T4',
      'T1',
      'T2',
      'T3',
      'RESULT',
      'CAUSE',
      'CLOSURE_CHECKS',
      'FINAL_EVIDENCE',
      'CLOSURE_COMMUNICATION',
      'DEVIATION_JUSTIFICATION',
    ]);
  });
});
