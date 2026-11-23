import { describe, expect, it, vi } from 'vitest';

import {
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { HandoverDialog } from './handover-dialog';

const people = [
  { id: 'u1', name: 'Ana Analista' },
  { id: 'u2', name: 'Segundo Analista' },
  { id: 'u3', name: 'Carlos Admin' },
];

function data(mode: 'handover' | 'assign', save = vi.fn().mockResolvedValue(undefined)) {
  return {
    mode,
    currentResponsibleId: 'u1',
    responsibles: vi.fn().mockResolvedValue(people),
    save,
  };
}

async function chooseIncoming(
  harness: Awaited<ReturnType<typeof openDialog<HandoverDialog>>>,
  name: string,
) {
  (harness.element.querySelector('mat-select') as HTMLElement).click();
  harness.fixture.detectChanges();
  (
    Array.from(document.querySelectorAll('mat-option')).find((o) =>
      o.textContent?.includes(name),
    ) as HTMLElement
  ).click();
  harness.fixture.detectChanges();
  await harness.fixture.whenStable();
}

describe('HandoverDialog', () => {
  it('el responsable actual no se ofrece como entrante', async () => {
    const harness = await openDialog(HandoverDialog, data('handover'));
    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    expect(
      Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent?.trim()),
    ).toEqual(['Segundo Analista', 'Carlos Admin']);
  });

  it('el relevo exige el entrante, el estado, los pendientes y el criterio de cierre', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(HandoverDialog, data('handover', save));

    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toEqual(
      expect.arrayContaining([
        'Seleccione a la persona',
        'Indique el estado actual',
        'Indique los pendientes',
        'Indique el criterio previsto de cierre',
      ]),
    );

    await chooseIncoming(harness, 'Segundo Analista');
    typeInto(harness.element, 'Estado actual', 'En observación');
    typeInto(harness.element, 'Pendientes', 'Confirmar estabilidad');
    typeInto(harness.element, 'Criterio previsto de cierre', 'Dos horas estable');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      incomingResponsibleId: 'u2',
      currentStatus: 'En observación',
      pending: 'Confirmar estabilidad',
      nextCheckAt: null,
      closureCriterion: 'Dos horas estable',
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('asignar solo pide a la persona', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(HandoverDialog, data('assign', save));

    expect(hasField(harness.element, 'Estado actual')).toBe(false);
    expect(harness.element.querySelector('h2')?.textContent).toContain('Asignar responsable');
    await chooseIncoming(harness, 'Carlos Admin');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith(expect.objectContaining({ incomingResponsibleId: 'u3' }));
  });

  it('si la lista no carga, avisa y no permite enviar', async () => {
    const harness = await openDialog(HandoverDialog, {
      ...data('handover'),
      responsibles: vi.fn().mockRejectedValue(new Error('sin red')),
    });
    expect(harness.element.textContent).toContain('No se pudo cargar la lista');
    expect(
      (harness.element.querySelector('button[type="submit"]') as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});
