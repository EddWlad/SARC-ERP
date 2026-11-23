import { describe, expect, it, vi } from 'vitest';

import {
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { EventDialog } from './event-dialog';

describe('EventDialog', () => {
  it('registra una observacion con el detalle y sin destino', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(EventDialog, { save });

    expect(hasField(harness.element, 'Destino del escalamiento')).toBe(false);
    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Escriba el detalle del evento');

    typeInto(harness.element, 'Detalle', '  Se observa estable  ');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      type: 'OBSERVATION',
      text: 'Se observa estable',
      occurredAt: null,
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('solo ofrece los cuatro tipos manuales', async () => {
    const harness = await openDialog(EventDialog, { save: vi.fn() });
    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    expect(
      Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent?.trim()),
    ).toEqual(['Validación', 'Acción correctiva', 'Escalamiento', 'Observación']);
  });

  it('un escalamiento exige su destino de una lista cerrada', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(EventDialog, { save });

    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    (
      Array.from(document.querySelectorAll('mat-option')).find((o) =>
        o.textContent?.includes('Escalamiento'),
      ) as HTMLElement
    ).click();
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();

    expect(hasField(harness.element, 'Destino del escalamiento')).toBe(true);
    typeInto(harness.element, 'Detalle', 'Se escaló a técnicos');
    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Seleccione el destino del escalamiento');
  });
});
