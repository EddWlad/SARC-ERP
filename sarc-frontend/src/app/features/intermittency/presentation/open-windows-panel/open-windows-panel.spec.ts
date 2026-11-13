import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { KiaPointResponse, WindowResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { OpenWindowsPanel } from './open-windows-panel';

const POINT: KiaPointResponse = {
  id: 'p1',
  name: 'Punto A',
  assignedCode: '900001',
  adminStatus: 'ACTIVE',
};

const OPEN: WindowResponse = {
  id: 'w1',
  version: 3,
  equipmentId: 'p1',
  pointLabel: 'Punto A (900001)',
  startAt: '2026-10-15T21:01:00Z',
  provisionalMinutes: 135,
  provisionalText: '2:15',
  state: 'OPEN',
  reviewReasons: [],
  source: 'MANUAL',
  revision: 1,
};

describe('OpenWindowsPanel', () => {
  const dialog = { open: vi.fn() };
  const notifier = { success: vi.fn(), error: vi.fn() };

  function render() {
    TestBed.configureTestingModule({
      imports: [OpenWindowsPanel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Notifier, useValue: notifier },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(OpenWindowsPanel);
    fixture.componentRef.setInput('point', POINT);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    return { fixture, http, root: fixture.nativeElement as HTMLElement };
  }

  const windowsRequest = (http: HttpTestingController) =>
    http.expectOne((request) => request.url === '/api/v1/intermittency/windows');

  beforeEach(() => {
    dialog.open.mockReset();
    notifier.success.mockReset();
  });

  it('lista las ventanas abiertas del punto con su duracion provisional', async () => {
    const { fixture, http, root } = render();
    const request = windowsRequest(http);
    expect(request.request.params.get('equipmentIds')).toBe('p1');
    expect(request.request.params.get('states')).toBe('OPEN');
    request.flush({ content: [OPEN], page: 0, size: 50, totalElements: 1, totalPages: 1 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(root.textContent).toContain('Ventanas abiertas de Punto A');
    expect(root.textContent).toContain('Duración provisional: 2:15');
    expect(root.textContent).toContain('no suma al');
    http.verify();
  });

  it('avisa cuando el punto no tiene ventanas abiertas', async () => {
    const { fixture, http, root } = render();
    windowsRequest(http).flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(root.textContent).toContain('no tiene ventanas abiertas');
  });

  it('no muestra nada sin un punto elegido', () => {
    TestBed.configureTestingModule({
      imports: [OpenWindowsPanel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Notifier, useValue: notifier },
      ],
    });
    const fixture = TestBed.createComponent(OpenWindowsPanel);
    fixture.componentRef.setInput('point', null);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('section')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('al registrar el fin avisa y recarga la lista', async () => {
    const { fixture, http, root } = render();
    windowsRequest(http).flush({
      content: [OPEN],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of({ ...OPEN, state: 'CONFIRMED' }) });

    (root.querySelector('li button') as HTMLButtonElement).click();

    expect(dialog.open).toHaveBeenCalledOnce();
    expect(notifier.success).toHaveBeenCalledWith('Fin registrado. La ventana quedó confirmada.');
    windowsRequest(http).flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
    http.verify();
  });

  it('si el diálogo se cancela no cambia nada', async () => {
    const { fixture, http, root } = render();
    windowsRequest(http).flush({
      content: [OPEN],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    fixture.detectChanges();
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    (root.querySelector('li button') as HTMLButtonElement).click();

    expect(notifier.success).not.toHaveBeenCalled();
    http.verify();
  });
});
