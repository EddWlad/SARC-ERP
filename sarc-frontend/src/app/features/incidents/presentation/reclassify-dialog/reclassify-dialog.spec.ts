import { describe, expect, it, vi } from 'vitest';

import { errors, openDialog, submitDialog, typeInto } from '../../testing/dialog-fixtures';
import { ReclassifyDialog } from './reclassify-dialog';

function optionLabels(): (string | undefined)[] {
  return Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent?.trim());
}

describe('ReclassifyDialog', () => {
  it('un equipo ofrece los tipos de cargador menos el vigente y ninguno del chatbot ni del sitio', async () => {
    const harness = await openDialog(ReclassifyDialog, {
      elementClass: 'EQUIPMENT',
      current: 'DISCONNECTION',
      save: vi.fn(),
    });
    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();

    const labels = optionLabels();
    expect(labels).not.toContain('Desconexión');
    expect(labels).toContain('Conector retenido en el vehículo');
    expect(labels).toContain('Incidencia no catalogada');
    expect(labels).not.toContain('Falla del servicio de chatbot');
    expect(labels).not.toContain('Falla de elemento del sitio');
  });

  it('el chatbot solo puede pasar a «Incidencia no catalogada»', async () => {
    const harness = await openDialog(ReclassifyDialog, {
      elementClass: 'CHATBOT_SERVICE',
      current: 'CHATBOT_SERVICE_FAILURE',
      save: vi.fn(),
    });
    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    expect(optionLabels()).toEqual(['Incidencia no catalogada']);
  });

  it('aclara que la prioridad vigente no cambia', async () => {
    const harness = await openDialog(ReclassifyDialog, {
      elementClass: 'EQUIPMENT',
      current: 'DISCONNECTION',
      save: vi.fn(),
    });
    expect(harness.element.textContent).toContain('Tipo actual: Desconexión');
    expect(harness.element.textContent).toContain('La prioridad vigente no cambia');
  });

  it('exige el nuevo tipo y el motivo, y luego guarda', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(ReclassifyDialog, {
      elementClass: 'CHATBOT_SERVICE',
      current: 'CHATBOT_SERVICE_FAILURE',
      save,
    });

    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toEqual(
      expect.arrayContaining([
        'Seleccione el nuevo tipo',
        'Indique el motivo de la reclasificación',
      ]),
    );

    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    (document.querySelector('mat-option') as HTMLElement).click();
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
    typeInto(harness.element, 'Motivo de la reclasificación', 'No es del servicio');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      incidentType: 'UNCATALOGED',
      reason: 'No es del servicio',
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });
});
