import { INTERMITTENCY_AVAILABILITY, INTERMITTENCY_EDITORS } from './intermittency-permissions';

describe('intermittency-permissions', () => {
  it('edita solo Administrador y Analista', () => {
    expect(INTERMITTENCY_EDITORS).toEqual(['ADMINISTRADOR', 'ANALISTA']);
  });

  it('la disponibilidad se ve solo con Administrador o Analista durante la prueba', () => {
    expect(INTERMITTENCY_AVAILABILITY).toEqual(['ADMINISTRADOR', 'ANALISTA']);
    for (const role of ['GERENTE', 'SUPERVISOR', 'ASISTENTE'] as const) {
      expect(INTERMITTENCY_AVAILABILITY).not.toContain(role);
    }
  });

  it('son listas distintas: cambiar una no cambia la otra', () => {
    expect(INTERMITTENCY_AVAILABILITY).not.toBe(INTERMITTENCY_EDITORS);
  });
});
