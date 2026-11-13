import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';

import { Role } from '../../../../core/auth/role';
import { SessionStore } from '../../../../core/auth/session-store';
import { WindowsFacade } from '../../application/windows-facade';
import { windowDetail, windowResponse } from '../../testing/window-fixtures';
import { IntermittencyWindowsPage } from './windows-page';

const OPEN = windowResponse({
  id: 'w2',
  state: 'OPEN',
  endAt: null,
  durationMinutes: null,
  provisionalMinutes: 135,
  source: 'XLSX',
});

describe('IntermittencyWindowsPage', () => {
  const facade = {
    point: signal(null),
    firstDay: signal(''),
    lastDay: signal(''),
    states: signal([]),
    pageIndex: signal(0),
    pageSize: signal(20),
    items: signal([windowResponse(), OPEN]),
    total: signal(2),
    loading: signal(false),
    error: signal(''),
    empty: signal(false),
    detailError: signal(''),
    load: vi.fn(),
    filter: vi.fn(),
    changePage: vi.fn(),
    openDetail: vi.fn(),
  };
  const dialog = { open: vi.fn() };

  function render(roles: readonly Role[]) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [IntermittencyWindowsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SessionStore,
          useValue: {
            hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles.includes(role)),
          },
        },
      ],
    });
    TestBed.overrideComponent(IntermittencyWindowsPage, {
      set: { providers: [{ provide: WindowsFacade, useValue: facade }] },
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(IntermittencyWindowsPage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    facade.load.mockReset().mockResolvedValue(undefined);
    facade.filter.mockReset().mockResolvedValue(undefined);
    facade.openDetail.mockReset();
    dialog.open.mockReset();
    facade.items.set([windowResponse(), OPEN]);
    facade.empty.set(false);
    facade.error.set('');
    facade.detailError.set('');
  });

  it('carga el historial al abrir y lista punto, horario, duracion, estado y origen', () => {
    const { root } = render(['ANALISTA']);

    expect(facade.load).toHaveBeenCalledOnce();
    const text = root.textContent ?? '';
    expect(text).toContain('Punto A (900001)');
    expect(text).toContain('2026-10-05 08:00 → 2026-10-05 08:30');
    expect(text).toContain('0:30 (30 min)');
    expect(text).toContain('Confirmada');
    expect(text).toContain('Captura manual');
  });

  it('una ventana abierta muestra su duracion como provisional', () => {
    const { root } = render(['ANALISTA']);

    expect(root.textContent).toContain('Abierta');
    expect(root.textContent).toContain('2:15 provisional');
    expect(root.textContent).toContain('abierta');
  });

  it('el editor puede filtrar por estado; el resto solo ve las confirmadas', () => {
    const editor = render(['ANALISTA']);
    expect(editor.root.querySelector('.filters mat-select')).not.toBeNull();
    expect(editor.root.textContent).not.toContain('Se muestran solo las ventanas confirmadas');

    const reader = render(['GERENTE']);
    expect(reader.root.querySelector('.filters mat-select')).toBeNull();
    expect(reader.root.textContent).toContain('Se muestran solo las ventanas confirmadas');
  });

  it('sin resultados muestra el estado vacio', () => {
    facade.empty.set(true);
    facade.items.set([]);
    const { root } = render(['GERENTE']);

    expect(root.textContent).toContain('No hay ventanas con estos filtros');
  });

  it('un error de carga se avisa con un reintento', () => {
    facade.error.set('Servicio no disponible.');
    const { root } = render(['GERENTE']);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Servicio no disponible.');
    const retry = Array.from(root.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Reintentar'),
    );
    retry!.click();
    expect(facade.load).toHaveBeenCalledTimes(2);
  });

  it('cambiar el primer dia filtra desde ese dia', () => {
    const { root } = render(['GERENTE']);
    const first = root.querySelector('input[type="date"]') as HTMLInputElement;

    first.value = '2026-10-01';
    first.dispatchEvent(new Event('change', { bubbles: true }));

    expect(facade.filter).toHaveBeenCalledWith({ firstDay: '2026-10-01' });
  });

  it('el detalle abre con el historial que devolvio el servidor', async () => {
    const detail = windowDetail();
    facade.openDetail.mockResolvedValue(detail);
    const { fixture, root } = render(['ANALISTA']);

    (root.querySelector('td button') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(facade.openDetail).toHaveBeenCalledWith('w1');
    expect(dialog.open).toHaveBeenCalledOnce();
    expect(dialog.open.mock.calls[0][1].data).toEqual({ detail });
  });

  it('si el detalle no se puede abrir no se abre el dialogo', async () => {
    facade.openDetail.mockResolvedValue(null);
    const { fixture, root } = render(['GERENTE']);

    (root.querySelector('td button') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(dialog.open).not.toHaveBeenCalled();
  });
});
