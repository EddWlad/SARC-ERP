import {
  classifiedRows,
  unclassifiedRows,
  visibleGroups,
} from '../../../shared/imports/import-confirm-summary';
import {
  EQUIPMENT_CLASSIFICATION_HINTS,
  EQUIPMENT_CLASSIFICATION_LABELS,
  EQUIPMENT_OUTCOME_LABELS,
  EquipmentImportBatch,
  equipmentClassificationCount,
  equipmentConfirmSummary,
} from './equipment-import';

/** Lote con las ocho clasificaciones, como lo devuelve el backend. Suma 20 filas. */
const BATCH = {
  fileName: 'cargadores.xlsx',
  totalRows: 20,
  newRows: 7,
  identicalRows: 4,
  modifiedRows: 3,
  identityCompletedRows: 2,
  invalidRows: 2,
  duplicateRows: 1,
  deletedExistsRows: 1,
  conflictRows: 0,
} as EquipmentImportBatch;

describe('clasificaciones de la carga masiva de equipos', () => {
  it('explica que completar identidad no requiere aprobación', () => {
    expect(EQUIPMENT_CLASSIFICATION_LABELS.IDENTITY_COMPLETED).toBe('Completan identidad');
    expect(EQUIPMENT_CLASSIFICATION_HINTS.IDENTITY_COMPLETED).toContain('sin aprobación');
    expect(EQUIPMENT_OUTCOME_LABELS.IDENTITY_ADDED).toBe('Identidad añadida');
  });

  it('advierte que un modificado sin aprobar tampoco registra su identidad', () => {
    expect(EQUIPMENT_CLASSIFICATION_HINTS.MODIFIED).toContain('tampoco');
  });

  it('cuenta las filas que completan identidad', () => {
    const batch = { identityCompletedRows: 95 } as EquipmentImportBatch;

    expect(equipmentClassificationCount(batch, 'IDENTITY_COMPLETED')).toBe(95);
  });
});

describe('resumen de confirmación de una carga masiva', () => {
  it('toma los conteos del lote, sin recalcularlos', () => {
    const counts = new Map(
      visibleGroups(equipmentConfirmSummary(BATCH)).flatMap((group) =>
        group.items.map((item) => [item.label, item.count] as const),
      ),
    );

    expect(counts.get(EQUIPMENT_CLASSIFICATION_LABELS.NEW)).toBe(BATCH.newRows);
    expect(counts.get(EQUIPMENT_CLASSIFICATION_LABELS.IDENTITY_COMPLETED)).toBe(
      BATCH.identityCompletedRows,
    );
    expect(counts.get(EQUIPMENT_CLASSIFICATION_LABELS.MODIFIED)).toBe(BATCH.modifiedRows);
    expect(counts.get(EQUIPMENT_CLASSIFICATION_LABELS.INVALID)).toBe(BATCH.invalidRows);
    expect(counts.get(EQUIPMENT_CLASSIFICATION_LABELS.DELETED_EXISTS)).toBe(
      BATCH.deletedExistsRows,
    );
  });

  it('cubre el total del lote con sus clasificaciones', () => {
    const resumen = equipmentConfirmSummary(BATCH);

    expect(classifiedRows(resumen)).toBe(BATCH.totalRows);
    expect(unclassifiedRows(resumen)).toBe(0);
  });

  it('separa lo que se aplica de lo que depende de una aprobación y de lo que no se toca', () => {
    const groups = visibleGroups(equipmentConfirmSummary(BATCH));
    const labelsOf = (tone: string) =>
      groups
        .filter((group) => group.tone === tone)
        .flatMap((group) => group.items.map((item) => item.label));

    expect(labelsOf('applies')).toEqual([
      EQUIPMENT_CLASSIFICATION_LABELS.NEW,
      EQUIPMENT_CLASSIFICATION_LABELS.IDENTITY_COMPLETED,
    ]);
    expect(labelsOf('conditional')).toEqual([EQUIPMENT_CLASSIFICATION_LABELS.MODIFIED]);
    expect(labelsOf('untouched')).toEqual([
      EQUIPMENT_CLASSIFICATION_LABELS.IDENTICAL,
      EQUIPMENT_CLASSIFICATION_LABELS.DUPLICATE,
      EQUIPMENT_CLASSIFICATION_LABELS.INVALID,
      EQUIPMENT_CLASSIFICATION_LABELS.DELETED_EXISTS,
    ]);
  });

  it('declara que el total de aprobaciones no está en el lote', () => {
    const conditional = equipmentConfirmSummary(BATCH).groups.find(
      (group) => group.tone === 'conditional',
    );

    expect(conditional?.note).toContain('no lleva el total de aprobaciones');
  });

  it('no ofrece conteo aparte: la carga de equipos no tiene umbral de energía', () => {
    expect(equipmentConfirmSummary(BATCH).aside).toBeUndefined();
  });

  it('nombra el archivo y el total tal como vienen del lote', () => {
    const resumen = equipmentConfirmSummary(BATCH);

    expect(resumen.fileName).toBe('cargadores.xlsx');
    expect(resumen.totalRows).toBe(20);
  });
});
