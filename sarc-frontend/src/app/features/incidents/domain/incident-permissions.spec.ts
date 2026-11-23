import { INCIDENT_EDITORS, INCIDENT_NUMBERING } from './incident-permissions';

describe('incident-permissions', () => {
  it('edita solo Administrador y Analista', () => {
    expect(INCIDENT_EDITORS).toEqual(['ADMINISTRADOR', 'ANALISTA']);
  });

  it('Gerente, Supervisor y Asistente no editan', () => {
    for (const role of ['GERENTE', 'SUPERVISOR', 'ASISTENTE'] as const) {
      expect(INCIDENT_EDITORS).not.toContain(role);
    }
  });

  it('la numeracion la configura solo el Administrador', () => {
    expect(INCIDENT_NUMBERING).toEqual(['ADMINISTRADOR']);
    expect(INCIDENT_NUMBERING).not.toContain('ANALISTA');
  });

  it('son listas distintas: cambiar una no cambia la otra', () => {
    expect(INCIDENT_NUMBERING).not.toBe(INCIDENT_EDITORS);
  });
});
