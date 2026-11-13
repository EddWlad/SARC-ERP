import { batch } from '../testing/import-fixtures';
import {
  blockingRows,
  classificationCount,
  IMPORT_CLASSIFICATIONS,
  importConfirmSummary,
} from './intermittency-import';
import {
  classifiedRows,
  unclassifiedRows,
  visibleGroups,
} from '../../../shared/imports/import-confirm-summary';

describe('intermittency-import', () => {
  it('cuenta las filas de cada clasificacion', () => {
    const current = batch();

    expect(classificationCount(current, 'NEW')).toBe(2);
    expect(classificationCount(current, 'INVALID')).toBe(5);
    expect(classificationCount(current, 'PENDING_LINK')).toBe(1);
    expect(
      IMPORT_CLASSIFICATIONS.map((value) => classificationCount(current, value)).reduce(
        (a, b) => a + b,
      ),
    ).toBe(13);
  });

  it('las filas que bloquean son invalidas, en conflicto, fuera de alcance y por vincular', () => {
    expect(blockingRows(batch())).toBe(5 + 2 + 1 + 1);
    expect(
      blockingRows(
        batch({
          counts: { ...batch().counts, invalid: 0, conflict: 0, outOfScope: 0, pendingLink: 0 },
        }),
      ),
    ).toBe(0);
  });

  it('el resumen de confirmar cubre todas las filas del lote con los conteos del propio lote', () => {
    const summary = importConfirmSummary(batch());

    expect(summary.fileName).toBe('registro.xlsx');
    expect(classifiedRows(summary)).toBe(2 + 1 + 2 + 1 + 1 + 5 + 1 + 1);
    expect(unclassifiedRows(summary)).toBe(0);
    expect(visibleGroups(summary).map((group) => group.title)).toEqual([
      'Se crean ventanas',
      'Dependen de lo que decidió',
      'No se tocan',
    ]);
  });

  it('un lote sin conflictos ni pendientes no muestra el grupo condicional', () => {
    const clean = batch({
      totalRows: 3,
      counts: {
        ...batch().counts,
        rows: 3,
        newRows: 3,
        identical: 0,
        conflict: 0,
        invalid: 0,
        pendingLink: 0,
        outOfScope: 0,
        review: 0,
        skipped: 0,
      },
    });

    expect(visibleGroups(importConfirmSummary(clean)).map((group) => group.title)).toEqual([
      'Se crean ventanas',
    ]);
  });
});
