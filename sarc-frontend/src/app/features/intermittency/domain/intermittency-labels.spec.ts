import {
  CLASSIFICATION_LABELS,
  DURATION_BAND_LABELS,
  issueText,
  PENDING_LINK_LABEL,
  reasonLabel,
  STATE_LABELS,
} from './intermittency-labels';

describe('intermittency-labels', () => {
  it('nombra los estados de una ventana en espanol', () => {
    expect(STATE_LABELS).toEqual({
      CONFIRMED: 'Confirmada',
      OPEN: 'Abierta',
      REVIEW: 'Por revisar',
      TEST: 'Prueba',
      ANNULLED: 'Anulada',
    });
    expect(PENDING_LINK_LABEL).toBe('Pendiente de vincular');
  });

  it('distingue la duplicada ya registrada de la duplicada en el lote', () => {
    expect(CLASSIFICATION_LABELS.IDENTICAL).toBe('Duplicada (ya registrada)');
    expect(reasonLabel('DUPLICATE_IN_BATCH')).toBe('Duplicada en el lote');
    expect(CLASSIFICATION_LABELS.CONFLICT).toBe('En conflicto');
  });

  it('cubre todas las clasificaciones de FR-025', () => {
    expect(Object.keys(CLASSIFICATION_LABELS).sort()).toEqual(
      ['CONFLICT', 'IDENTICAL', 'INVALID', 'NEW', 'OUT_OF_SCOPE', 'PENDING_LINK', 'REVIEW'].sort(),
    );
  });

  it('un codigo desconocido se muestra tal cual en lugar de ocultarse', () => {
    expect(reasonLabel('OTRO_CODIGO')).toBe('OTRO_CODIGO');
    expect(reasonLabel('END_BEFORE_START')).toBe('El fin es anterior o igual al inicio');
  });

  it('rotula las cinco franjas de duracion', () => {
    expect(Object.values(DURATION_BAND_LABELS)).toEqual([
      'Menos de 5 min',
      '5 a 14 min',
      '15 a 59 min',
      '1 h a menos de 4 h',
      '4 h o más',
    ]);
  });

  it('usa el texto con tildes de la interfaz y, si no conoce el motivo, el del servidor', () => {
    expect(issueText({ code: 'UNKNOWN_CODE', message: 'No hay un punto con ese codigo' })).toBe(
      'No hay un punto con ese código',
    );
    expect(issueText({ code: 'NAME_DIFFERS', message: '' })).toContain('se vincula por el código');
    expect(issueText({ code: 'NUEVO_MOTIVO', message: 'Texto del servidor' })).toBe(
      'Texto del servidor',
    );
  });
});
