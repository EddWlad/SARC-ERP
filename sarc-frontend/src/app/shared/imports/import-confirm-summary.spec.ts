import {
  classifiedRows,
  ImportConfirmSummary,
  unclassifiedRows,
  visibleGroups,
} from './import-confirm-summary';

function summary(overrides: Partial<ImportConfirmSummary> = {}): ImportConfirmSummary {
  return {
    title: 'Confirmar',
    fileName: 'lote.xlsx',
    totalRows: 10,
    confirmLabel: 'Confirmar',
    groups: [
      {
        title: 'Se aplican',
        tone: 'applies',
        items: [
          { label: 'Nuevas', count: 6 },
          { label: 'Se vinculan', count: 0 },
        ],
      },
      {
        title: 'No se tocan',
        tone: 'untouched',
        items: [
          { label: 'Idénticas', count: 3 },
          { label: 'Inválidas', count: 1 },
        ],
      },
      { title: 'Sin filas', tone: 'conditional', items: [{ label: 'Modificadas', count: 0 }] },
    ],
    ...overrides,
  };
}

describe('resumen de confirmación de un lote', () => {
  it('oculta las clasificaciones sin filas y los grupos que quedan vacíos', () => {
    const groups = visibleGroups(summary());

    expect(groups.map((group) => group.title)).toEqual(['Se aplican', 'No se tocan']);
    expect(groups[0].items.map((item) => item.label)).toEqual(['Nuevas']);
  });

  it('conserva el tono y la nota del grupo al filtrar', () => {
    const groups = visibleGroups(
      summary({
        groups: [
          {
            title: 'Solo las decididas',
            tone: 'conditional',
            note: 'El lote no lleva el total de decisiones tomadas.',
            items: [
              { label: 'Modificadas', count: 2 },
              { label: 'Cambian de cargador', count: 0 },
            ],
          },
        ],
      }),
    );

    expect(groups).toHaveLength(1);
    expect(groups[0].tone).toBe('conditional');
    expect(groups[0].note).toBe('El lote no lleva el total de decisiones tomadas.');
  });

  it('suma las filas clasificadas sin contar el aparte', () => {
    const withAside = summary({
      aside: { label: 'Lecturas superiores a 1.000 kWh', count: 4 },
    });

    expect(classifiedRows(withAside)).toBe(10);
    expect(unclassifiedRows(withAside)).toBe(0);
  });

  it('nombra las filas del total que ninguna clasificación cubre', () => {
    expect(unclassifiedRows(summary({ totalRows: 14 }))).toBe(4);
  });

  it('no inventa un resto cuando las clasificaciones exceden el total', () => {
    expect(unclassifiedRows(summary({ totalRows: 4 }))).toBe(0);
  });

  it('cuenta cero en un lote vacío', () => {
    const empty = summary({
      totalRows: 0,
      groups: [{ title: 'Se aplican', tone: 'applies', items: [{ label: 'Nuevas', count: 0 }] }],
    });

    expect(visibleGroups(empty)).toEqual([]);
    expect(classifiedRows(empty)).toBe(0);
    expect(unclassifiedRows(empty)).toBe(0);
  });
});
