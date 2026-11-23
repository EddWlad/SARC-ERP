import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';

import { localInput } from '../../domain/incident-time';
import {
  alertText,
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { CommunicationDialog } from './communication-dialog';

async function choose(
  harness: Awaited<ReturnType<typeof openDialog<CommunicationDialog>>>,
  label: string,
  option: string,
): Promise<void> {
  const field = Array.from(harness.element.querySelectorAll('mat-form-field')).find((f) =>
    f.querySelector('mat-label')?.textContent?.includes(label),
  );
  (field?.querySelector('mat-select') as HTMLElement).click();
  harness.fixture.detectChanges();
  (
    Array.from(document.querySelectorAll('mat-option')).find((o) =>
      o.textContent?.includes(option),
    ) as HTMLElement
  ).click();
  harness.fixture.detectChanges();
  await harness.fixture.whenStable();
}

describe('CommunicationDialog', () => {
  it('propone el tipo recibido y la hora actual, y la evidencia es opcional', async () => {
    const harness = await openDialog(CommunicationDialog, {
      suggestedKind: 'CLOSURE',
      save: vi.fn(),
    });

    const text = harness.element.textContent ?? '';
    expect(text).toContain('Cierre');
    expect(text).toContain('Evidencia (opcional)');
    expect(text).toContain('Copiar el mensaje no lo registra');
    const time = harness.element.querySelector('input[type="datetime-local"]') as HTMLInputElement;
    expect(time.value).toBe(localInput(new Date()));
  });

  it('exige el canal y no guarda sin el', async () => {
    const save = vi.fn();
    const harness = await openDialog(CommunicationDialog, { suggestedKind: 'INITIAL', save });

    await submitDialog(harness);

    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Seleccione el canal');
  });

  it('«Otro» pide cual fue el canal y los demas no', async () => {
    const harness = await openDialog(CommunicationDialog, {
      suggestedKind: 'INITIAL',
      save: vi.fn(),
    });
    expect(hasField(harness.element, '¿Qué canal?')).toBe(false);

    await choose(harness, 'Canal', 'Otro');
    expect(hasField(harness.element, '¿Qué canal?')).toBe(true);

    await submitDialog(harness);
    expect(errors(harness.element)).toContain('Indique qué canal fue');
  });

  it('guarda el canal, el tipo y la hora real con la hora de Ecuador', async () => {
    const save = vi.fn().mockResolvedValue(null);
    const harness = await openDialog(CommunicationDialog, { suggestedKind: 'UPDATE', save });

    await choose(harness, 'Canal', 'Grupo KIA electrolineras');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledTimes(1);
    const [result, evidence] = save.mock.calls[0];
    expect(result).toMatchObject({
      channel: 'KIA_ELECTROLINERAS',
      kind: 'UPDATE',
      channelDetail: null,
      justification: null,
    });
    expect(Math.abs(new Date(result.sentAt).getTime() - Date.now())).toBeLessThan(120_000);
    expect(evidence).toBeNull();
    expect(harness.close).toHaveBeenCalledWith(null);
  });

  it('una hora anterior exige justificacion y se envia con ella', async () => {
    const save = vi.fn().mockResolvedValue(null);
    const harness = await openDialog(CommunicationDialog, { suggestedKind: 'INITIAL', save });

    await choose(harness, 'Canal', 'Directivos');
    typeInto(harness.element, 'Hora real del envío', '2020-01-01T10:00');
    harness.fixture.detectChanges();
    expect(hasField(harness.element, 'Justificación de la hora anterior')).toBe(true);
    await submitDialog(harness);
    expect(save).not.toHaveBeenCalled();
    expect(errors(harness.element)).toContain('Una hora anterior exige una justificación');

    typeInto(harness.element, 'Justificación de la hora anterior', 'Se avisó por llamada');
    await submitDialog(harness);
    expect(save.mock.calls[0][0]).toMatchObject({
      sentAt: '2020-01-01T15:00:00.000Z',
      justification: 'Se avisó por llamada',
    });
  });

  it('la hora propuesta no se vuelve retroactiva aunque pasen unos segundos: se envia la hora del guardado', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-22T21:54:50Z'));
    const save = vi.fn().mockResolvedValue(null);
    const harness = await openDialog(CommunicationDialog, { suggestedKind: 'INITIAL', save });
    await choose(harness, 'Canal', 'Directivos');

    // El analista tarda 40 s: la hora propuesta (16:54) ya es 90 s anterior al momento de guardar.
    vi.setSystemTime(new Date('2026-11-22T21:55:30Z'));
    harness.fixture.detectChanges();
    expect(hasField(harness.element, 'Justificación de la hora anterior')).toBe(false);
    await submitDialog(harness);
    vi.useRealTimers();

    expect(save).toHaveBeenCalledTimes(1);
    expect(save.mock.calls[0][0].sentAt).toBe('2026-11-22T21:55:30.000Z');
    expect(save.mock.calls[0][0].justification).toBeNull();
  });

  it('la hora de ahora no pide justificacion', async () => {
    const harness = await openDialog(CommunicationDialog, {
      suggestedKind: 'INITIAL',
      save: vi.fn(),
    });
    expect(hasField(harness.element, 'Justificación de la hora anterior')).toBe(false);
  });

  it('entrega la evidencia elegida y el aviso si no se pudo cargar', async () => {
    const save = vi.fn().mockResolvedValue('Formato no permitido');
    const harness = await openDialog(CommunicationDialog, { suggestedKind: 'INITIAL', save });
    const input = harness.element.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['%PDF-'], 'captura.pdf');
    Object.defineProperty(input, 'files', { value: { item: () => file }, configurable: true });
    input.dispatchEvent(new Event('change'));
    harness.fixture.detectChanges();
    expect(harness.element.textContent).toContain('captura.pdf');

    await choose(harness, 'Canal', 'Correo electrónico');
    await submitDialog(harness);

    expect(save.mock.calls[0][1]).toBe(file);
    expect(harness.close).toHaveBeenCalledWith('Formato no permitido');
  });

  it('rechaza en el dialogo un archivo vacio o de mas de 10 MB', async () => {
    const harness = await openDialog(CommunicationDialog, {
      suggestedKind: 'INITIAL',
      save: vi.fn(),
    });
    const input = harness.element.querySelector('input[type="file"]') as HTMLInputElement;
    const pick = (file: File) => {
      Object.defineProperty(input, 'files', { value: { item: () => file }, configurable: true });
      input.dispatchEvent(new Event('change'));
      harness.fixture.detectChanges();
    };

    pick(new File([], 'vacio.pdf'));
    expect(harness.element.textContent).toContain('El archivo está vacío');
    pick(new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'grande.pdf'));
    expect(harness.element.textContent).toContain('supera el límite de 10 MB');
  });

  it('un error del servidor se muestra y no cierra el dialogo', async () => {
    const save = vi.fn().mockRejectedValue(
      new HttpErrorResponse({
        status: 422,
        error: {
          code: 'TICKET_EVENT_INVALID',
          detail: 'La hora del envío no puede estar en el futuro',
        },
      }),
    );
    const harness = await openDialog(CommunicationDialog, { suggestedKind: 'INITIAL', save });

    await choose(harness, 'Canal', 'Directivos');
    await submitDialog(harness);

    expect(alertText(harness.element)).toContain('no puede estar en el futuro');
    expect(harness.close).not.toHaveBeenCalled();
  });
});
