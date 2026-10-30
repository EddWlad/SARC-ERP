import { LINK_ISSUE_LABELS } from './consumption';
import {
  CLASSIFICATION_HINTS,
  CLASSIFICATION_LABELS,
  ImportBatch,
  OUTCOME_LABELS,
  classificationCount,
} from './energy-import';

describe('clasificaciones de la importación de sesiones', () => {
  it('distingue las sesiones que se vinculan de las que siguen pendientes', () => {
    expect(CLASSIFICATION_LABELS.LINKABLE).not.toBe(CLASSIFICATION_LABELS.PENDING_LINK);
    expect(CLASSIFICATION_HINTS.LINKABLE).toContain('al confirmar');
    expect(OUTCOME_LABELS.LINKED).toBe('Vinculada');
  });

  it('cuenta aparte vinculables y pendientes', () => {
    const batch = { linkableRows: 3, pendingLinkRows: 7 } as ImportBatch;

    expect(classificationCount(batch, 'LINKABLE')).toBe(3);
    expect(classificationCount(batch, 'PENDING_LINK')).toBe(7);
  });

  it('nombra cada motivo de una sesión pendiente', () => {
    expect(LINK_ISSUE_LABELS.IDENTITY_AMBIGUOUS).toBe('Identidad Enerlink ambigua');
    expect(new Set(Object.values(LINK_ISSUE_LABELS)).size).toBe(4);
  });
});
