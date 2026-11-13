import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { PendingRowResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { ReviewFacade } from '../../application/review-facade';
import { row } from '../../testing/import-fixtures';
import { windowResponse } from '../../testing/window-fixtures';
import { JustificationDialogData } from '../justification-dialog/justification-dialog';
import { IntermittencyReviewPage } from './review-page';

// 13:00Z a 17:30Z son 08:00 a 12:30 en la zona operativa: 4 h 30 min.
const LONG = windowResponse({
  id: 'w1',
  state: 'REVIEW',
  reviewReasons: ['LONG_WINDOW'],
  startAt: '2026-10-05T13:00:00Z',
  endAt: '2026-10-05T17:30:00Z',
  durationMinutes: 270,
  durationText: '4:30',
});
const OPEN = windowResponse({
  id: 'w2',
  state: 'OPEN',
  endAt: null,
  durationMinutes: null,
  provisionalMinutes: 60,
});
const TEST = windowResponse({ id: 'w3', state: 'TEST' });
const PENDING: PendingRowResponse = {
  batchId: 'b1',
  fileName: 'registro.xlsx',
  batchCreatedAt: '2026-10-06T14:00:00Z',
  row: row({ id: 'r9', rowNumber: 7, classification: 'PENDING_LINK' }),
};

describe('IntermittencyReviewPage', () => {
  const facade = {
    section: signal<'REVIEW' | 'OPEN' | 'TEST' | 'PENDING'>('REVIEW'),
    windows: signal([LONG]),
    pending: signal<PendingRowResponse[]>([]),
    counts: signal({ REVIEW: 1, OPEN: 2, TEST: 3, PENDING: 4 }),
    loading: signal(false),
    error: signal(''),
    empty: signal(false),
    load: vi.fn(),
    select: vi.fn(),
    confirmReview: vi.fn(),
    markTest: vi.fn(),
    unmarkTest: vi.fn(),
    annul: vi.fn(),
    correct: vi.fn(),
    close: vi.fn(),
    rowActions: vi.fn(),
    previewPointTest: vi.fn(),
    pointTest: vi.fn(),
  };
  const dialog = { open: vi.fn() };
  const notifier = { success: vi.fn(), error: vi.fn() };

  function render() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [IntermittencyReviewPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Notifier, useValue: notifier },
      ],
    });
    TestBed.overrideComponent(IntermittencyReviewPage, {
      set: { providers: [{ provide: ReviewFacade, useValue: facade }] },
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(IntermittencyReviewPage);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const button = (label: string) =>
      Array.from(root.querySelectorAll('button')).find(
        (candidate) => candidate.textContent?.trim() === label,
      ) as HTMLButtonElement;
    return { fixture, root, button };
  }

  beforeEach(() => {
    for (const mock of [
      dialog.open,
      notifier.success,
      facade.load,
      facade.select,
      facade.rowActions,
    ]) {
      mock.mockReset();
    }
    facade.section.set('REVIEW');
    facade.windows.set([LONG]);
    facade.pending.set([]);
    facade.empty.set(false);
    facade.error.set('');
  });

  it('muestra las secciones con su cantidad y carga al abrir', () => {
    const { root } = render();

    expect(facade.load).toHaveBeenCalledOnce();
    const text = root.textContent ?? '';
    for (const label of [
      'Por revisar (1)',
      'Abiertas (2)',
      'Prueba (3)',
      'Pendientes de lotes (4)',
    ]) {
      expect(text).toContain(label);
    }
  });

  it('cambiar de seccion la pide a la fachada', () => {
    const { root } = render();

    (root.querySelectorAll('mat-button-toggle button')[1] as HTMLButtonElement).click();

    expect(facade.select).toHaveBeenCalledWith('OPEN');
  });

  it('una ventana larga muestra la duracion junto a sus instantes completos y el motivo', () => {
    const { root } = render();

    const text = root.textContent ?? '';
    expect(text).toContain('2026-10-05 08:00 → 2026-10-05 12:30');
    expect(text).toContain('4:30 (270 min)');
    expect(text).toContain('Dura 4 horas o más');
    expect(text).toContain('Por revisar');
  });

  it('cada estado ofrece solo las acciones que le corresponden', () => {
    facade.windows.set([LONG]);
    const review = render();
    expect(review.button('Confirmar')).toBeTruthy();
    expect(review.button('Marcar prueba')).toBeTruthy();
    expect(review.button('Corregir')).toBeTruthy();
    expect(review.button('Anular')).toBeTruthy();

    facade.section.set('OPEN');
    facade.windows.set([OPEN]);
    const open = render();
    expect(open.button('Registrar fin')).toBeTruthy();
    expect(open.button('Corregir')).toBeUndefined();
    expect(open.root.textContent).toContain('1:00 (60 min) provisional');

    facade.section.set('TEST');
    facade.windows.set([TEST]);
    const test = render();
    expect(test.button('Quitar prueba')).toBeTruthy();
    expect(test.button('Confirmar')).toBeUndefined();
  });

  it('confirmar pide justificacion, la envia a la fachada y avisa al terminar', async () => {
    const { fixture, button } = render();
    facade.confirmReview.mockResolvedValue(LONG);
    dialog.open.mockReturnValue({ afterClosed: () => of(LONG) });

    button('Confirmar').click();
    await fixture.whenStable();

    const data = dialog.open.mock.calls[0][1].data as JustificationDialogData;
    expect(data.title).toBe('Confirmar la revisión');
    expect(data.target).toContain('Punto A (900001)');
    await data.submit('Verificado con el operador');
    expect(facade.confirmReview).toHaveBeenCalledWith(LONG, 'Verificado con el operador');
    expect(notifier.success).toHaveBeenCalledWith(
      'Revisión confirmada: la ventana ya cuenta en lo oficial.',
    );
  });

  it('si el dialogo se cancela no se avisa nada', async () => {
    const { fixture, button } = render();
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    button('Anular').click();
    await fixture.whenStable();

    expect(notifier.success).not.toHaveBeenCalled();
  });

  it('corregir abre el formulario con una funcion que usa la fachada', async () => {
    const { fixture, button } = render();
    facade.correct.mockResolvedValue(LONG);
    dialog.open.mockReturnValue({ afterClosed: () => of(LONG) });

    button('Corregir').click();
    await fixture.whenStable();

    const data = dialog.open.mock.calls[0][1].data;
    expect(data.window).toBe(LONG);
    await data.submit({ comment: 'x', justification: 'y' });
    expect(facade.correct).toHaveBeenCalledWith(LONG, { comment: 'x', justification: 'y' });
    expect(notifier.success).toHaveBeenCalledWith(
      'Corrección guardada. Se conservó la versión anterior.',
    );
  });

  it('registrar el fin de una abierta usa la fachada como quien cierra', async () => {
    facade.section.set('OPEN');
    facade.windows.set([OPEN]);
    const { fixture, button } = render();
    dialog.open.mockReturnValue({ afterClosed: () => of({ ...OPEN, state: 'REVIEW' }) });

    button('Registrar fin').click();
    await fixture.whenStable();

    expect(dialog.open.mock.calls[0][1].data).toEqual({ window: OPEN, closer: facade });
    expect(notifier.success).toHaveBeenCalledWith(
      'Fin registrado. La ventana dura 4 horas o más y quedó por revisar.',
    );
  });

  it('las filas pendientes de lotes confirmados se resuelven con el panel de decisiones', async () => {
    facade.section.set('PENDING');
    facade.windows.set([]);
    facade.pending.set([PENDING]);
    const actions = { editRow: vi.fn(), linkRow: vi.fn(), decideRow: vi.fn() };
    facade.rowActions.mockReturnValue(actions);
    const { fixture, root, button } = render();
    dialog.open.mockReturnValue({ afterClosed: () => of(PENDING.row) });

    expect(root.textContent).toContain('registro.xlsx');
    expect(root.textContent).toContain('fila 7');
    expect(root.textContent).toContain('Pendiente de vincular');
    button('Resolver').click();
    await fixture.whenStable();

    expect(facade.rowActions).toHaveBeenCalledWith(PENDING);
    expect(dialog.open.mock.calls[0][1].data).toEqual({
      row: PENDING.row,
      actions,
      resolving: true,
    });
    expect(notifier.success).toHaveBeenCalledWith('Fila resuelta.');
  });

  it('una seccion sin elementos muestra su estado vacio', () => {
    facade.section.set('TEST');
    facade.windows.set([]);
    facade.empty.set(true);
    const { root } = render();

    expect(root.textContent).toContain('No hay ventanas de prueba.');
  });

  it('un error de carga se avisa con un reintento', () => {
    facade.error.set('Servicio no disponible.');
    const { root, button } = render();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Servicio no disponible.');
    button('Reintentar ahora').click();
    expect(facade.load).toHaveBeenCalledTimes(2);
  });

  it('marcar prueba por punto abre el dialogo con la fachada', async () => {
    const { fixture, button } = render();
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    button('Marcar prueba por punto').click();
    await fixture.whenStable();

    expect(dialog.open.mock.calls[0][1].data).toEqual({ actions: facade });
  });
});
