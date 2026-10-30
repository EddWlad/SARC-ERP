import {
  EQUIPMENT_CLASSIFICATION_HINTS,
  EQUIPMENT_CLASSIFICATION_LABELS,
  EQUIPMENT_OUTCOME_LABELS,
  EquipmentImportBatch,
  equipmentClassificationCount,
} from './equipment-import';

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
