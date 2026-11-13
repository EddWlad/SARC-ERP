import {
  IntermittencyImportBatch,
  IntermittencyImportRow,
  RowWindow,
} from '../domain/intermittency-import';

/** Datos de prueba de la vista previa: lo que el backend devolveria, con codigos ficticios. */
export function window(overrides: Partial<RowWindow> = {}): RowWindow {
  return {
    pair: 1,
    startText: '08:00',
    endText: '08:30',
    startAt: '2026-10-05T08:00:00-05:00',
    endAt: '2026-10-05T08:30:00-05:00',
    minutes: 30,
    endsNextDay: false,
    test: false,
    added: false,
    classification: 'NEW',
    issues: [],
    ...overrides,
  };
}

export function row(overrides: Partial<IntermittencyImportRow> = {}): IntermittencyImportRow {
  return {
    id: 'r1',
    version: 0,
    rowNumber: 3,
    classification: 'NEW',
    raw: [
      { key: 'date', label: 'Fecha', value: '2026-10-05' },
      { key: 'code', label: 'Código cargador', value: '900001' },
    ],
    edits: [],
    interpretation: {
      date: '2026-10-05',
      code: '900001',
      name: 'Punto A',
      comment: 'Intermitencia',
      fileTotalMinutes: 30,
      computedTotalMinutes: 30,
      totalAcknowledged: false,
      rowIssues: [],
      windows: [window()],
    },
    touchedDays: ['2026-10-05'],
    ...overrides,
  };
}

export function batch(overrides: Partial<IntermittencyImportBatch> = {}): IntermittencyImportBatch {
  return {
    id: 'b1',
    version: 2,
    status: 'READY',
    origin: 'FILE',
    format: 'XLSX',
    fileName: 'registro.xlsx',
    totalRows: 14,
    checksum: 'abc',
    counts: {
      rows: 14,
      windows: 20,
      newRows: 2,
      identical: 1,
      conflict: 2,
      invalid: 5,
      pendingLink: 1,
      outOfScope: 1,
      review: 1,
      skipped: 1,
    },
    appliedRows: 0,
    createdAt: '2026-10-20T15:00:00Z',
    ...overrides,
  };
}
