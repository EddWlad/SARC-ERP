import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

import { windowDetail, windowResponse } from '../../testing/window-fixtures';
import { WindowDetailDialog, WindowDetailDialogData } from './window-detail-dialog';

describe('WindowDetailDialog', () => {
  function render(data: WindowDetailDialogData) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: MAT_DIALOG_DATA, useValue: data }] });
    const fixture = TestBed.createComponent(WindowDetailDialog);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra los instantes completos, la duracion y el origen', () => {
    const root = render({ detail: windowDetail({ rowNumber: 3, pairNumber: 2, batchId: 'b1' }) });

    const text = root.textContent ?? '';
    expect(text).toContain('Ventana de Punto A (900001)');
    expect(text).toContain('2026-10-05 08:00');
    expect(text).toContain('2026-10-05 08:30');
    expect(text).toContain('0:30 (30 min)');
    expect(text).toContain('Captura manual');
    expect(text).toContain('fila 3, par 2');
  });

  it('el editor ve el historial con la justificacion de cada cambio', () => {
    const root = render({ detail: windowDetail() });

    const history = root.querySelector('[aria-label="Historial de revisiones"]');
    expect(history?.querySelectorAll('li')).toHaveLength(2);
    expect(history?.textContent).toContain('Corregida');
    expect(history?.textContent).toContain('Justificación: Se corrigió el fin según el reporte');
    expect(history?.textContent).toContain('Registrada');
  });

  it('sin revisiones (otros roles) no hay historial', () => {
    const root = render({ detail: windowDetail({ revisions: null }) });

    expect(root.querySelector('[aria-label="Historial de revisiones"]')).toBeNull();
    expect(root.textContent).not.toContain('Historial');
  });

  it('una ventana abierta rotula la duracion como provisional y su fin como abierta', () => {
    const root = render({
      detail: windowDetail({
        window: windowResponse({
          state: 'OPEN',
          endAt: null,
          durationMinutes: null,
          provisionalMinutes: 135,
        }),
        revisions: null,
      }),
    });

    expect(root.textContent).toContain('Duración provisional');
    expect(root.textContent).toContain('2:15 (135 min)');
    expect(root.textContent).toContain('Abierta');
  });

  it('avisa cuando el punto cambio de nombre despues del registro', () => {
    const root = render({ detail: windowDetail({ currentPointName: 'Punto A renombrado' }) });

    expect(root.textContent).toContain('Nombre actual del punto');
    expect(root.textContent).toContain('Punto A renombrado');
  });
});
