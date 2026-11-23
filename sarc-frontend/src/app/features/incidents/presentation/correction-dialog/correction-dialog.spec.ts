import { describe, expect, it, vi } from 'vitest';

import {
  alertText,
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { CorrectionDialog } from './correction-dialog';

function data(save = vi.fn().mockResolvedValue(undefined)) {
  return {
    description: 'Sin comunicación',
    reportOrigin: 'PLATFORM',
    reportOriginDetail: null,
    save,
  };
}

describe('CorrectionDialog', () => {
  it('sin ningun cambio avisa y no guarda', async () => {
    const save = vi.fn();
    const harness = await openDialog(CorrectionDialog, data(save));

    typeInto(harness.element, 'Justificación de la corrección', 'Revisión');
    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(alertText(harness.element)).toContain('No hay ningún cambio que corregir');
  });

  it('toda correccion exige justificacion', async () => {
    const save = vi.fn();
    const harness = await openDialog(CorrectionDialog, data(save));

    typeInto(harness.element, 'Descripción', 'Descripción corregida');
    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Una corrección exige una justificación');
  });

  it('envia solo el campo que cambio, con su justificacion', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(CorrectionDialog, data(save));

    typeInto(harness.element, 'Descripción', '  Descripción corregida ');
    typeInto(harness.element, 'Justificación de la corrección', 'Dato mal escrito');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      justification: 'Dato mal escrito',
      description: 'Descripción corregida',
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('el elemento afectado no se corrige aqui y lo explica', async () => {
    const harness = await openDialog(CorrectionDialog, data());
    expect(hasField(harness.element, 'Elemento')).toBe(false);
    expect(harness.element.textContent).toContain('Registrado por error');
  });

  it('el origen «Otro» exige su detalle', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(CorrectionDialog, {
      ...data(save),
      reportOrigin: 'OTHER',
      reportOriginDetail: 'Llamada',
    });
    expect(hasField(harness.element, 'Detalle del origen')).toBe(true);

    typeInto(harness.element, 'Detalle del origen', '');
    typeInto(harness.element, 'Justificación de la corrección', 'Aclaración');
    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Indique el detalle del origen');
  });
});
