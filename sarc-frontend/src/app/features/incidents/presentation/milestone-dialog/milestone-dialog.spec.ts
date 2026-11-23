import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';

import { TicketMilestone } from '../../../../core/api/generated';
import {
  alertText,
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { MilestoneDialog } from './milestone-dialog';

function milestone(overrides: Partial<TicketMilestone> = {}): TicketMilestone {
  return {
    kind: 'T1',
    status: 'PENDING',
    retroactive: false,
    orderWarningAck: false,
    recordedAt: '2026-11-21T15:00:00Z',
    ...overrides,
  };
}

describe('MilestoneDialog', () => {
  it('registrar un hito pendiente con la hora vacia guarda el momento actual sin pedir motivo', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(MilestoneDialog, { kind: 'T1', current: milestone(), save });

    expect(hasField(harness.element, 'Motivo')).toBe(false);
    expect(hasField(harness.element, 'Justificación')).toBe(false);
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      status: 'RECORDED',
      occurredAt: null,
      reason: null,
      justification: null,
      orderWarningAck: false,
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('una hora anterior exige motivo y no guarda sin el', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(MilestoneDialog, { kind: 'T1', current: milestone(), save });

    typeInto(harness.element, 'Hora', '2020-01-01T10:00');
    harness.fixture.detectChanges();
    expect(hasField(harness.element, 'Motivo de la hora anterior')).toBe(true);
    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Indique el motivo');

    typeInto(harness.element, 'Motivo de la hora anterior', 'Se validó por teléfono');
    await submitDialog(harness);
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 'RECORDED',
        occurredAt: '2020-01-01T15:00:00.000Z',
        reason: 'Se validó por teléfono',
      }),
    );
  });

  it('corregir un hito ya registrado exige una justificacion', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(MilestoneDialog, {
      kind: 'T2',
      current: milestone({ kind: 'T2', status: 'RECORDED', occurredAt: '2026-11-21T15:20:00Z' }),
      save,
    });

    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Una corrección exige una justificación');

    typeInto(harness.element, 'Justificación de la corrección', 'Hora mal leída');
    typeInto(harness.element, 'Motivo de la hora anterior', 'Se confirmó con el proveedor');
    await submitDialog(harness);
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ justification: 'Hora mal leída' }));
  });

  it('T0 y T4 solo se registran: no ofrecen No aplica ni Sin dato', async () => {
    for (const kind of ['T0', 'T4'] as const) {
      const harness = await openDialog(MilestoneDialog, {
        kind,
        current: milestone({ kind }),
        save: vi.fn(),
      });
      expect(hasField(harness.element, 'Estado del hito')).toBe(false);
    }
  });

  it('el estado No aplica oculta la hora y exige un motivo', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(MilestoneDialog, {
      kind: 'T2',
      current: milestone({ kind: 'T2' }),
      save,
    });

    const select = harness.element.querySelector('mat-select') as HTMLElement;
    select.click();
    harness.fixture.detectChanges();
    const option = Array.from(document.querySelectorAll('mat-option')).find((o) =>
      o.textContent?.includes('No aplica'),
    ) as HTMLElement;
    option.click();
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();

    expect(hasField(harness.element, 'Hora')).toBe(false);
    expect(hasField(harness.element, 'Motivo')).toBe(true);
    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
  });

  it('si el servidor advierte el orden, ofrece confirmarlo y reenvia con la confirmacion', async () => {
    const warning = new HttpErrorResponse({
      status: 422,
      error: { code: 'MILESTONE_ORDER_UNCONFIRMED', detail: 'T2 no puede ser anterior a T1' },
    });
    const save = vi.fn().mockRejectedValueOnce(warning).mockResolvedValueOnce(undefined);
    const harness = await openDialog(MilestoneDialog, { kind: 'T1', current: milestone(), save });

    await submitDialog(harness);
    expect(alertText(harness.element)).toContain('T2 no puede ser anterior a T1');
    const checkbox = harness.element.querySelector('mat-checkbox') as HTMLElement;
    expect(checkbox.textContent).toContain('Confirmo el orden de los hitos');
    expect(harness.close).not.toHaveBeenCalled();

    (checkbox.querySelector('input') as HTMLInputElement).click();
    harness.fixture.detectChanges();
    await submitDialog(harness);

    expect(save).toHaveBeenLastCalledWith(expect.objectContaining({ orderWarningAck: true }));
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('un error del servidor se muestra en el dialogo y no lo cierra', async () => {
    const save = vi.fn().mockRejectedValue(
      new HttpErrorResponse({
        status: 422,
        error: { code: 'MILESTONE_IN_FUTURE', detail: 'La hora está en el futuro' },
      }),
    );
    const harness = await openDialog(MilestoneDialog, { kind: 'T1', current: milestone(), save });

    await submitDialog(harness);

    expect(alertText(harness.element)).toContain('La hora está en el futuro');
    expect(harness.close).not.toHaveBeenCalled();
  });

  it('en un episodio cerrado todo cambio es una correccion y exige justificacion, aunque el hito siga pendiente', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(MilestoneDialog, {
      kind: 'T1',
      current: milestone(),
      closed: true,
      save,
    });

    expect(hasField(harness.element, 'Justificación de la corrección')).toBe(true);
    expect(harness.element.querySelector('h2')?.textContent).toContain('Corregir');
    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Una corrección exige una justificación');
  });
});
