import {
  classifiedRows,
  unclassifiedRows,
  visibleGroups,
} from '../../../shared/imports/import-confirm-summary';
import { LINK_ISSUE_LABELS } from './consumption';
import {
  CLASSIFICATION_HINTS,
  CLASSIFICATION_LABELS,
  ImportBatch,
  OUTCOME_LABELS,
  classificationCount,
  sessionConfirmSummary,
} from './energy-import';

/**
 * Lote con las nueve clasificaciones, como lo devuelve el backend. Las clasificaciones suman las 25
 * filas; `energyReviewRows` es un conteo aparte y no entra en esa suma.
 */
const BATCH = {
  fileName: 'SessionsReport.xlsx',
  totalRows: 25,
  newRows: 5,
  linkableRows: 2,
  deletedRows: 1,
  modifiedRows: 3,
  equipmentChangedRows: 1,
  pendingLinkRows: 4,
  identicalRows: 6,
  duplicateRows: 2,
  invalidRows: 1,
  energyReviewRows: 3,
} as ImportBatch;

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

describe('resumen de confirmación de una importación de sesiones', () => {
  it('toma los conteos del lote, sin recalcularlos', () => {
    const counts = new Map(
      visibleGroups(sessionConfirmSummary(BATCH)).flatMap((group) =>
        group.items.map((item) => [item.label, item.count] as const),
      ),
    );

    expect(counts.get(CLASSIFICATION_LABELS.NEW)).toBe(BATCH.newRows);
    expect(counts.get(CLASSIFICATION_LABELS.LINKABLE)).toBe(BATCH.linkableRows);
    expect(counts.get(CLASSIFICATION_LABELS.MODIFIED)).toBe(BATCH.modifiedRows);
    expect(counts.get(CLASSIFICATION_LABELS.EQUIPMENT_CHANGED)).toBe(BATCH.equipmentChangedRows);
    expect(counts.get(CLASSIFICATION_LABELS.PENDING_LINK)).toBe(BATCH.pendingLinkRows);
    expect(counts.get(CLASSIFICATION_LABELS.IDENTICAL)).toBe(BATCH.identicalRows);
    expect(counts.get(CLASSIFICATION_LABELS.DUPLICATE)).toBe(BATCH.duplicateRows);
    expect(counts.get(CLASSIFICATION_LABELS.INVALID)).toBe(BATCH.invalidRows);
    expect(counts.get(CLASSIFICATION_LABELS.DELETED)).toBe(BATCH.deletedRows);
  });

  it('cubre el total del lote con sus clasificaciones', () => {
    const resumen = sessionConfirmSummary(BATCH);

    expect(classifiedRows(resumen)).toBe(BATCH.totalRows);
    expect(unclassifiedRows(resumen)).toBe(0);
  });

  it('separa lo que se incorpora, lo que depende de una decisión y lo que no se toca', () => {
    const groups = visibleGroups(sessionConfirmSummary(BATCH));
    const labelsOf = (tone: string) =>
      groups
        .filter((group) => group.tone === tone)
        .flatMap((group) => group.items.map((item) => item.label));

    expect(labelsOf('applies')).toEqual([
      CLASSIFICATION_LABELS.NEW,
      CLASSIFICATION_LABELS.LINKABLE,
    ]);
    expect(labelsOf('conditional')).toEqual([
      CLASSIFICATION_LABELS.MODIFIED,
      CLASSIFICATION_LABELS.EQUIPMENT_CHANGED,
      CLASSIFICATION_LABELS.PENDING_LINK,
    ]);
    expect(labelsOf('untouched')).toEqual([
      CLASSIFICATION_LABELS.IDENTICAL,
      CLASSIFICATION_LABELS.DUPLICATE,
      CLASSIFICATION_LABELS.DELETED,
      CLASSIFICATION_LABELS.INVALID,
    ]);
  });

  it('deja las lecturas sobre el umbral como conteo aparte, fuera de las clasificaciones', () => {
    const resumen = sessionConfirmSummary(BATCH);
    const inGroups = resumen.groups
      .flatMap((group) => group.items)
      .some((item) => item.label === resumen.aside?.label);

    expect(resumen.aside?.count).toBe(BATCH.energyReviewRows);
    expect(resumen.aside?.label).toContain('1.000 kWh');
    expect(inGroups).toBe(false);
  });

  it('declara que el total de decisiones tomadas no está en el lote', () => {
    const conditional = sessionConfirmSummary(BATCH).groups.find(
      (group) => group.tone === 'conditional' && group.note !== undefined,
    );

    expect(conditional?.note).toContain('no lleva el total de decisiones');
  });

  it('nombra el resto si el lote trae una clasificación que la pantalla no cubre', () => {
    const conMas = { ...BATCH, totalRows: 27 } as ImportBatch;

    expect(unclassifiedRows(sessionConfirmSummary(conMas))).toBe(2);
  });

  it('no muestra clasificaciones sin filas', () => {
    const soloNuevas = {
      ...BATCH,
      totalRows: 5,
      linkableRows: 0,
      deletedRows: 0,
      modifiedRows: 0,
      equipmentChangedRows: 0,
      pendingLinkRows: 0,
      identicalRows: 0,
      duplicateRows: 0,
      invalidRows: 0,
      energyReviewRows: 0,
    } as ImportBatch;

    const groups = visibleGroups(sessionConfirmSummary(soloNuevas));

    expect(groups).toHaveLength(1);
    expect(groups[0].items).toEqual([
      { label: CLASSIFICATION_LABELS.NEW, count: 5, detail: expect.any(String) },
    ]);
  });
});
