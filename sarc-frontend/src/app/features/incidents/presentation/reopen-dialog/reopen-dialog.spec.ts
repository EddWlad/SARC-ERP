import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';

import {
  alertText,
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { ReopenDialog } from './reopen-dialog';

describe('ReopenDialog', () => {
  it('nombra el ticket y explica que conserva su número y sus episodios', async () => {
    const harness = await openDialog(ReopenDialog, { code: 'TICKET-9001', save: vi.fn() });
    const text = harness.element.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(text).toContain('Reabrir TICKET-9001');
    expect(text).toContain('conserva su número y sus episodios anteriores');
    expect(hasField(harness.element, 'Motivo de la hora anterior')).toBe(false);
  });

  it('exige el motivo y no guarda sin el', async () => {
    const save = vi.fn();
    const harness = await openDialog(ReopenDialog, { code: 'TICKET-9001', save });

    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Indique el motivo de la reapertura');
  });

  it('con la hora vacía reabre con el momento actual', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(ReopenDialog, { code: 'TICKET-9001', save });

    typeInto(harness.element, 'Motivo de la reapertura', '  La falla volvió  ');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({ reason: 'La falla volvió', t0: null, t0Reason: null });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('una hora anterior exige su propio motivo y se envía con él', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(ReopenDialog, { code: 'TICKET-9001', save });

    typeInto(harness.element, 'Motivo de la reapertura', 'La falla volvió');
    typeInto(harness.element, 'Hora de la nueva detección', '2020-01-01T10:00');
    harness.fixture.detectChanges();
    expect(hasField(harness.element, 'Motivo de la hora anterior')).toBe(true);
    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Una hora anterior exige un motivo');

    typeInto(harness.element, 'Motivo de la hora anterior', 'Se detectó antes de registrar');
    await submitDialog(harness);
    expect(save).toHaveBeenCalledWith({
      reason: 'La falla volvió',
      t0: '2020-01-01T15:00:00.000Z',
      t0Reason: 'Se detectó antes de registrar',
    });
  });

  it('un error del servidor se muestra y no cierra el diálogo', async () => {
    const save = vi.fn().mockRejectedValue(
      new HttpErrorResponse({
        status: 409,
        error: {
          code: 'TICKET_REOPEN_FORBIDDEN',
          detail: 'Un ticket cerrado como duplicado no se reabre',
        },
      }),
    );
    const harness = await openDialog(ReopenDialog, { code: 'TICKET-9001', save });

    typeInto(harness.element, 'Motivo de la reapertura', 'La falla volvió');
    await submitDialog(harness);

    expect(alertText(harness.element)).toContain('no se reabre');
    expect(harness.close).not.toHaveBeenCalled();
  });
});
