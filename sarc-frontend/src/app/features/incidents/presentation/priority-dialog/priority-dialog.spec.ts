import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';

import {
  alertText,
  errors,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { PriorityDialog } from './priority-dialog';

async function choose(
  harness: Awaited<ReturnType<typeof openDialog<PriorityDialog>>>,
  label: string,
): Promise<void> {
  (harness.element.querySelector('mat-select') as HTMLElement).click();
  harness.fixture.detectChanges();
  (
    Array.from(document.querySelectorAll('mat-option')).find((o) =>
      o.textContent?.includes(label),
    ) as HTMLElement
  ).click();
  harness.fixture.detectChanges();
  await harness.fixture.whenStable();
}

describe('PriorityDialog', () => {
  it('no ofrece la prioridad vigente, porque no seria un cambio', async () => {
    const harness = await openDialog(PriorityDialog, { current: 'HIGH', save: vi.fn() });
    expect(harness.element.textContent).toContain('Prioridad actual: Alta');
    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    expect(
      Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent?.trim()),
    ).toEqual(['Media', 'Crítica']);
  });

  it('exige la nueva prioridad y el motivo', async () => {
    const save = vi.fn();
    const harness = await openDialog(PriorityDialog, { current: 'HIGH', save });

    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toEqual(
      expect.arrayContaining(['Seleccione la nueva prioridad', 'Indique el motivo del cambio']),
    );
  });

  it('guarda la prioridad elegida con su motivo y cierra', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(PriorityDialog, { current: 'HIGH', save });

    await choose(harness, 'Crítica');
    typeInto(harness.element, 'Motivo del cambio', '  Afecta a una electrolinera  ');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      priority: 'CRITICAL',
      reason: 'Afecta a una electrolinera',
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('un error del servidor se muestra en el dialogo y no lo cierra', async () => {
    const save = vi.fn().mockRejectedValue(
      new HttpErrorResponse({
        status: 409,
        error: {
          code: 'TICKET_VERSION_CONFLICT',
          detail: 'El ticket fue modificado por otro usuario',
        },
      }),
    );
    const harness = await openDialog(PriorityDialog, { current: 'HIGH', save });

    await choose(harness, 'Media');
    typeInto(harness.element, 'Motivo del cambio', 'Se resolvió');
    await submitDialog(harness);

    expect(alertText(harness.element)).toContain('modificado por otro usuario');
    expect(harness.close).not.toHaveBeenCalled();
  });
});
