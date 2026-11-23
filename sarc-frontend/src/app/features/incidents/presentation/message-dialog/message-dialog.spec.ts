import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MessageResponse } from '../../../../core/api/generated';
import { alertText, openDialog } from '../../testing/dialog-fixtures';
import { MessageDialog } from './message-dialog';

const BASE_TEXT = '*TICKET-9001*\nCargador/Electrolinera: KIA Norte\nEstado: Abierto';

function response(overrides: Partial<MessageResponse> = {}): MessageResponse {
  return {
    text: BASE_TEXT,
    missingFields: [],
    ticketCode: 'TICKET-9001',
    state: 'OPEN',
    ...overrides,
  };
}

describe('MessageDialog', () => {
  const writeText = vi.fn();

  beforeEach(() => {
    writeText.mockReset().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  });

  afterEach(() => vi.useRealTimers());

  const textarea = (element: HTMLElement) =>
    element.querySelector('textarea') as HTMLTextAreaElement;
  const button = (element: HTMLElement, label: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(label)) as
      HTMLButtonElement | undefined;
  const checkboxes = (element: HTMLElement) =>
    Array.from(element.querySelectorAll('mat-checkbox input')) as HTMLInputElement[];

  it('al abrirse pide el texto sin opciones y deja las dos casillas desactivadas', async () => {
    const load = vi.fn().mockResolvedValue(response());
    const harness = await openDialog(MessageDialog, { load });

    expect(load).toHaveBeenCalledTimes(1);
    expect(load).toHaveBeenCalledWith({ includeDetection: false, includeActions: false });
    expect(textarea(harness.element).value).toBe(BASE_TEXT);
    expect(checkboxes(harness.element).map((c) => c.checked)).toEqual([false, false]);
    expect(harness.element.textContent).toContain('Incluir hora de detección (T0)');
    expect(harness.element.textContent).toContain('Incluir acciones realizadas');
  });

  it('cada vez que se abre las opciones vuelven a empezar desactivadas', async () => {
    const load = vi.fn().mockResolvedValue(response());
    const first = await openDialog(MessageDialog, { load });
    checkboxes(first.element)[0].click();
    first.fixture.detectChanges();
    await first.fixture.whenStable();
    expect(load).toHaveBeenLastCalledWith({ includeDetection: true, includeActions: false });

    const second = await openDialog(MessageDialog, { load });
    expect(checkboxes(second.element).map((c) => c.checked)).toEqual([false, false]);
    expect(load).toHaveBeenLastCalledWith({ includeDetection: false, includeActions: false });
  });

  it('activar una opcion vuelve a pedir el texto con ella', async () => {
    const load = vi
      .fn()
      .mockResolvedValueOnce(response())
      .mockResolvedValueOnce(response({ text: BASE_TEXT + '\nDetección: 21/11/2026 10:30' }));
    const harness = await openDialog(MessageDialog, { load });

    checkboxes(harness.element)[0].click();
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(load).toHaveBeenLastCalledWith({ includeDetection: true, includeActions: false });
    expect(textarea(harness.element).value).toContain('Detección: 21/11/2026 10:30');
  });

  it('copiar entrega al portapapeles el texto tal como esta editado, sin llamar al servidor', async () => {
    const load = vi.fn().mockResolvedValue(response());
    const harness = await openDialog(MessageDialog, { load });

    const area = textarea(harness.element);
    area.value = BASE_TEXT + '\nPróxima verificación: 17:00';
    area.dispatchEvent(new Event('input'));
    button(harness.element, 'Copiar')?.click();
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(writeText).toHaveBeenCalledWith(BASE_TEXT + '\nPróxima verificación: 17:00');
    expect(load).toHaveBeenCalledTimes(1);
    expect(button(harness.element, 'Copiado')).toBeDefined();
    expect(harness.element.querySelector('[role="status"]')?.textContent).toContain(
      'Texto copiado',
    );
  });

  it('editar el texto no vuelve a pedirlo ni cierra el dialogo', async () => {
    const load = vi.fn().mockResolvedValue(response());
    const harness = await openDialog(MessageDialog, { load });

    const area = textarea(harness.element);
    area.value = 'otro texto';
    area.dispatchEvent(new Event('input'));
    harness.fixture.detectChanges();

    expect(load).toHaveBeenCalledTimes(1);
    expect(harness.close).not.toHaveBeenCalled();
  });

  it('sin enlace de Maps avisa con discrecion y deja copiar igual', async () => {
    const load = vi.fn().mockResolvedValue(response({ missingFields: ['mapsUrl'] }));
    const harness = await openDialog(MessageDialog, { load });

    expect(harness.element.textContent).toContain('Falta el enlace de Google Maps');
    const copy = button(harness.element, 'Copiar');
    expect(copy?.disabled).toBe(false);
    copy?.click();
    await harness.fixture.whenStable();
    expect(writeText).toHaveBeenCalledTimes(1);
  });

  it('sin ubicacion ni Maps nombra los dos', async () => {
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response({ missingFields: ['location', 'mapsUrl'] })),
    });
    expect(harness.element.textContent).toContain('Falta la ubicación y el enlace de Google Maps');
  });

  it('si todo esta completo no muestra ningun aviso de faltantes', async () => {
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
    });
    expect(harness.element.textContent).not.toContain('Falta');
  });

  it('muestra la nota fija del Protocolo §4.1 sin afirmar que cumple', async () => {
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
    });
    const text = harness.element.textContent ?? '';
    expect(text).toContain('Protocolo §4.1');
    expect(text).toContain(
      'la fuente de validación, la solicitud concreta y la próxima verificación',
    );
    expect(text.toLowerCase()).not.toContain('cumple');
  });

  it('«Registrar envío» solo se ofrece si quien abre puede registrarlo, y «Copiar» es la acción principal', async () => {
    const readOnly = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
    });
    expect(button(readOnly.element, 'Registrar envío')).toBeUndefined();
    expect(button(readOnly.element, 'Copiar')?.getAttribute('matbutton')).toBe('filled');

    const editor = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
      record: vi.fn().mockResolvedValue(false),
    });
    expect(button(editor.element, 'Registrar envío')).toBeDefined();
    expect(button(editor.element, 'Copiar')?.getAttribute('matbutton')).toBe('filled');
  });

  it('registrar el envio cierra el mensaje solo si se registro', async () => {
    const record = vi.fn().mockResolvedValueOnce(false).mockResolvedValueOnce(true);
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
      record,
    });

    button(harness.element, 'Registrar envío')?.click();
    await harness.fixture.whenStable();
    expect(harness.close).not.toHaveBeenCalled();

    button(harness.element, 'Registrar envío')?.click();
    await harness.fixture.whenStable();
    expect(harness.close).toHaveBeenCalled();
  });

  it('copiar nunca registra una comunicacion: solo existe lo que el dialogo de envio confirma', async () => {
    const record = vi.fn();
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
      record,
    });
    button(harness.element, 'Copiar')?.click();
    await harness.fixture.whenStable();
    expect(record).not.toHaveBeenCalled();
  });

  it('si el portapapeles falla y no hay plan B, lo dice sin perder el texto', async () => {
    writeText.mockRejectedValue(new Error('denegado'));
    document.execCommand = vi.fn().mockReturnValue(false);
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockResolvedValue(response()),
    });

    button(harness.element, 'Copiar')?.click();
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(alertText(harness.element)).toContain('No se pudo copiar');
    expect(textarea(harness.element).value).toBe(BASE_TEXT);
  });

  it('un error al cargar el texto se muestra y no deja copiar un texto vacio', async () => {
    const harness = await openDialog(MessageDialog, {
      load: vi.fn().mockRejectedValue({ status: 404, error: { detail: 'El ticket no existe' } }),
    });
    expect(alertText(harness.element)).toBeDefined();
    expect(button(harness.element, 'Copiar')?.disabled).toBe(true);
  });
});
