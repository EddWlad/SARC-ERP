import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { ReviewedDayResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { ReviewedDaysFacade } from '../../application/reviewed-days-facade';
import { JustificationDialogData } from '../justification-dialog/justification-dialog';
import { ReviewedDaysPanel } from './reviewed-days-panel';

function day(date: string, overrides: Partial<ReviewedDayResponse> = {}): ReviewedDayResponse {
  return {
    day: date,
    active: false,
    finished: true,
    pendingLinkRows: 0,
    unconfirmedBatches: 0,
    ...overrides,
  };
}

describe('ReviewedDaysPanel', () => {
  const facade = {
    days: signal<readonly ReviewedDayResponse[]>([]),
    today: signal('2026-10-20'),
    month: signal(''),
    chooseMonth: vi.fn(),
    showRecent: vi.fn(),
    loading: signal(false),
    error: signal(''),
    load: vi.fn(),
    mark: vi.fn(),
    withdraw: vi.fn(),
  };
  const dialog = { open: vi.fn() };
  const notifier = { success: vi.fn(), error: vi.fn() };

  function render(days: ReviewedDayResponse[]) {
    TestBed.resetTestingModule();
    facade.days.set(days);
    TestBed.configureTestingModule({
      imports: [ReviewedDaysPanel],
      providers: [{ provide: Notifier, useValue: notifier }],
    });
    TestBed.overrideComponent(ReviewedDaysPanel, {
      set: { providers: [{ provide: ReviewedDaysFacade, useValue: facade }] },
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(ReviewedDaysPanel);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const button = (label: string) =>
      Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((candidate) =>
        candidate.getAttribute('aria-label')?.startsWith(label),
      ) as HTMLButtonElement;
    return { root, button };
  }

  const text = (root: HTMLElement) => root.textContent?.replace(/\s+/g, ' ') ?? '';

  beforeEach(() => {
    for (const mock of [
      dialog.open,
      notifier.success,
      notifier.error,
      facade.load,
      facade.mark,
      facade.withdraw,
      facade.chooseMonth,
      facade.showRecent,
    ]) {
      mock.mockReset();
    }
    facade.month.set('');
    facade.error.set('');
    facade.loading.set(false);
  });

  it('carga los dias al abrir y muestra su estado: revisado, sin datos o en curso', () => {
    const { root } = render([
      day('2026-10-20', { finished: false }),
      day('2026-10-19', { active: true, markedAt: '2026-10-20T01:30:00Z', version: 0 }),
      day('2026-10-18'),
    ]);

    expect(facade.load).toHaveBeenCalledOnce();
    expect(text(root)).toContain('En curso');
    expect(text(root)).toContain('Revisado');
    expect(text(root)).toContain('Sin datos');
    expect(text(root)).toContain('Se puede marcar cuando el día termine.');
    expect(text(root)).toContain('nunca cuenta como 100 %');
  });

  it('hoy y los dias futuros no se pueden marcar', () => {
    const { button } = render([day('2026-10-20', { finished: false })]);

    expect(button('Marcar como revisado el').disabled).toBe(true);
  });

  it('marca un dia terminado y avisa', async () => {
    facade.mark.mockResolvedValue({ day: day('2026-10-19', { active: true }), warnings: [] });
    const { button } = render([day('2026-10-19')]);

    button('Marcar como revisado el').click();
    await Promise.resolve();
    await Promise.resolve();

    expect(facade.mark).toHaveBeenCalledOnce();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(notifier.success).toHaveBeenCalledWith(expect.stringContaining('marcado como revisado'));
  });

  it('con lotes sin confirmar que tocan el dia pide confirmacion antes de marcar', async () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    facade.mark.mockResolvedValue({ day: day('2026-10-19', { active: true }), warnings: [] });
    const { root, button } = render([day('2026-10-19', { unconfirmedBatches: 2 })]);
    expect(text(root)).toContain('2 lotes sin confirmar');

    button('Marcar como revisado el').click();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(dialog.open).toHaveBeenCalledOnce();
    expect(dialog.open.mock.calls[0][1].data.message).toContain('2 lotes en vista previa');
    expect(facade.mark).toHaveBeenCalledOnce();
  });

  it('si no confirma el aviso no marca el dia', async () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    const { button } = render([day('2026-10-19', { unconfirmedBatches: 1 })]);

    button('Marcar como revisado el').click();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(facade.mark).not.toHaveBeenCalled();
  });

  it('si el servidor rechaza la marca avisa con su mensaje', async () => {
    facade.mark.mockRejectedValue(new Error('Solo se marca un dia que ya termino'));
    const { button } = render([day('2026-10-19')]);

    button('Marcar como revisado el').click();
    await Promise.resolve();
    await Promise.resolve();

    expect(notifier.error).toHaveBeenCalledWith('Solo se marca un dia que ya termino');
  });

  it('retirar la marca pide justificacion y usa la version del dia', async () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    const marked = day('2026-10-19', { active: true, version: 4 });
    const { button } = render([marked]);

    button('Retirar la marca del').click();
    await Promise.resolve();
    await Promise.resolve();

    const data = dialog.open.mock.calls[0][1].data as JustificationDialogData;
    expect(data.title).toBe('Retirar la marca de revisado');
    await data.submit('Se marco por error');
    expect(facade.withdraw).toHaveBeenCalledWith(marked, 'Se marco por error');
    expect(notifier.success).toHaveBeenCalledWith(expect.stringContaining('sin datos'));
  });

  it('las filas por vincular se muestran como lo que deja el dia sin cubrir', () => {
    const { root } = render([day('2026-10-19', { pendingLinkRows: 3 })]);

    expect(text(root)).toContain('3 filas por vincular');
    expect(text(root)).toContain('dejan este día sin cubrir para toda la red');
  });

  it('un error se muestra con un reintento', () => {
    facade.error.set('No tiene permisos para esta operacion');
    const { root } = render([]);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('No tiene permisos');
    (root.querySelector('.state button') as HTMLButtonElement).click();
    expect(facade.load).toHaveBeenCalledTimes(2);
  });

  it('por defecto muestra los ultimos 31 dias y ofrece elegir un mes pasado para marcar el historico', () => {
    const { root } = render([day('2026-10-19')]);
    const input = root.querySelector('input[type="month"]') as HTMLInputElement;

    expect(text(root)).toContain('Mostrando los últimos 31 días.');
    // El mes no puede ser posterior al vigente, pero si cualquiera anterior.
    expect(input.max).toBe('2026-10');
    expect(root.textContent).not.toContain('Últimos 31 días Mostrando');

    input.value = '2026-08';
    input.dispatchEvent(new Event('change'));

    expect(facade.chooseMonth).toHaveBeenCalledWith('2026-08');
  });

  it('con un mes elegido lo rotula y permite volver a los ultimos 31 dias', () => {
    facade.month.set('2026-08');
    const { root, button } = render([day('2026-08-31')]);

    expect(text(root)).toMatch(/Mostrando agosto de 2026\./);
    const back = Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidate) => candidate.textContent?.trim() === 'Últimos 31 días',
    ) as HTMLButtonElement;
    back.click();

    expect(facade.showRecent).toHaveBeenCalledOnce();
    expect(button('Marcar como revisado el').disabled).toBe(false);
  });

  it('un mes vacio no es un periodo: el campo vuelve al mes elegido', () => {
    facade.month.set('2026-08');
    const { root } = render([]);
    const input = root.querySelector('input[type="month"]') as HTMLInputElement;

    input.value = '';
    input.dispatchEvent(new Event('change'));

    expect(facade.chooseMonth).not.toHaveBeenCalled();
    expect(input.value).toBe('2026-08');
  });

  it('si el servidor devuelve lotes sin confirmar que la lista no mostraba, lo avisa tras marcar', async () => {
    facade.mark.mockResolvedValue({
      day: day('2026-10-19', { active: true }),
      warnings: [{ batchId: 'b1', fileName: 'turno.csv' }],
    });
    const { button } = render([day('2026-10-19')]);

    button('Marcar como revisado el').click();
    await Promise.resolve();
    await Promise.resolve();

    expect(dialog.open).not.toHaveBeenCalled();
    const message = notifier.success.mock.calls[0][0] as string;
    expect(message).toContain('quedó marcado como revisado');
    expect(message).toContain('1 lote sin confirmar tiene filas de este día (turno.csv)');
  });
});
