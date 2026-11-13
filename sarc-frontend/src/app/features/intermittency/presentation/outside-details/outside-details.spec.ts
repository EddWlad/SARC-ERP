import { TestBed } from '@angular/core/testing';

import { IntermittencySummary } from '../../domain/intermittency-summary';
import { summary } from '../../testing/summary-fixtures';
import { OutsideDetails } from './outside-details';

type Details = NonNullable<IntermittencySummary['outsideOfficial']['details']>;

describe('OutsideDetails', () => {
  const details: Details = {
    windows: [
      {
        windowId: 'w1',
        equipmentId: 'p1',
        pointLabel: 'Punto A (900001)',
        state: 'REVIEW',
        startAt: '2026-10-07T13:00:00Z',
        endAt: '2026-10-07T18:00:00Z',
        minutes: 300,
        reasons: ['LONG_WINDOW'],
      },
      {
        windowId: 'w2',
        equipmentId: 'p1',
        pointLabel: 'Punto A (900001)',
        state: 'OPEN',
        startAt: '2026-10-09T13:00:00Z',
        minutes: 135,
        reasons: [],
      },
    ],
    pendingRows: [{ batchId: 'b1', rowNumber: 4, classification: 'PENDING_LINK' }],
    truncated: false,
  };

  function render(value: Details): HTMLElement {
    TestBed.resetTestingModule();
    const fixture = TestBed.createComponent(OutsideDetails);
    fixture.componentRef.setInput('details', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (root: HTMLElement) => root.textContent?.replace(/\s+/g, ' ') ?? '';

  it('lista las ventanas con su estado, sus instantes, su tiempo y el motivo de revision', () => {
    const detail = text(render(details));

    expect(detail).toContain('Ver el detalle (3)');
    expect(detail).toContain('Por revisar');
    expect(detail).toContain('Punto A (900001)');
    expect(detail).toContain('2026-10-07 08:00 → 2026-10-07 13:00');
    expect(detail).toContain('5:00 h');
    expect(detail).toContain('Dura 4 horas o más');
  });

  it('una abierta rotula su tiempo como provisional', () => {
    const detail = text(render(details));

    expect(detail).toContain('Abierta');
    expect(detail).toContain('abierta · 2:15 h provisional');
  });

  it('lista las filas pendientes de lotes confirmados', () => {
    expect(text(render(details))).toContain('Fila 4 de un lote confirmado');
  });

  it('avisa cuando el servidor acoto el detalle', () => {
    expect(text(render({ ...details, truncated: true }))).toContain('Se muestran los primeros 100');
    expect(text(render(details))).not.toContain('Se muestran los primeros 100');
  });

  it('sin nada que listar no dibuja el desplegable', () => {
    const root = render({ windows: [], pendingRows: [], truncated: false });

    expect(root.querySelector('details')).toBeNull();
    // El resumen tambien funciona con cualquier fixture del tablero.
    expect(summary().outsideOfficial.details).toBeUndefined();
  });
});
