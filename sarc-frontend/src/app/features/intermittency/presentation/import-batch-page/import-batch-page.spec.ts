import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ImportBatchHeader } from '../../../../shared/imports/import-batch-header/import-batch-header';
import { ImportClassificationFilter } from '../../../../shared/imports/import-classification-filter';
import { IntermittencyImportBatchFacade } from '../../application/import-batch-facade';
import { batch, row, window } from '../../testing/import-fixtures';
import { IntermittencyImportBatchPage } from './import-batch-page';

describe('IntermittencyImportBatchPage', () => {
  function render(options: { reviewable?: boolean; rows?: ReturnType<typeof row>[] } = {}) {
    const facade = {
      batch: signal(batch()),
      rows: signal(options.rows ?? [row()]),
      rowsTotal: signal(1),
      rowsLoading: signal(false),
      reviewable: signal(options.reviewable ?? true),
      notFound: signal(false),
      failed: signal(false),
      classification: signal(''),
      pageIndex: signal(0),
      pageSize: signal(50),
      filterBy: vi.fn(),
      changePage: vi.fn(),
      reload: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [IntermittencyImportBatchPage],
      providers: [provideRouter([])],
    });
    TestBed.overrideComponent(IntermittencyImportBatchPage, {
      set: { providers: [{ provide: IntermittencyImportBatchFacade, useValue: facade }] },
    });
    TestBed.overrideComponent(IntermittencyImportBatchPage, {
      remove: { imports: [ImportBatchHeader, ImportClassificationFilter] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(IntermittencyImportBatchPage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement, facade };
  }

  it('muestra por fila la fecha, el codigo, el valor original, la interpretacion y la clasificacion', () => {
    const { root } = render({
      rows: [
        row({
          classification: 'INVALID',
          interpretation: {
            ...row().interpretation,
            windows: [
              window({
                startText: '19:41',
                endText: '19:26',
                startAt: '2026-10-05T19:41:00-05:00',
                endAt: '2026-10-05T19:26:00-05:00',
                minutes: null,
                classification: 'INVALID',
                issues: [
                  { code: 'END_BEFORE_START', message: 'El fin es anterior o igual al inicio' },
                ],
              }),
            ],
          },
        }),
      ],
    });

    const text = root.textContent ?? '';
    expect(text).toContain('05/10/2026');
    expect(text).toContain('900001');
    expect(text).toContain('Original: 19:41 → 19:26');
    expect(text).toContain('2026-10-05 19:41');
    expect(text).toContain('El fin es anterior o igual al inicio');
    expect(text).toContain('Inválida');
  });

  it('muestra los totales con y sin el duplicado', () => {
    const { root } = render({
      rows: [
        row({
          classification: 'CONFLICT',
          interpretation: {
            ...row().interpretation,
            fileTotalMinutes: 109,
            computedTotalMinutes: 83,
            computedWithDuplicatesMinutes: 109,
          },
        }),
      ],
    });

    expect(root.textContent).toContain('Total del archivo 1:49');
    expect(root.textContent).toContain('recalculado 1:23');
    expect(root.textContent).toContain('con el duplicado 1:49');
  });

  it('avisa cuantas filas bloquean la confirmacion', () => {
    const { root } = render();

    expect(root.querySelector('.blocking')?.textContent).toContain('Quedan 9 filas por resolver');
  });

  it('en revision ofrece corregir y resolver; fuera de ella, no', () => {
    const review = render();
    expect(review.root.textContent).toContain('Corregir');
    expect(review.root.textContent).toContain('Resolver');

    TestBed.resetTestingModule();
    const closed = render({ reviewable: false });
    expect(closed.root.textContent).not.toContain('Corregir');
    expect(closed.root.textContent).not.toContain('Resolver');
  });

  it('rotula las ventanas con su decision, su marca de prueba y su fusion', () => {
    const { root } = render({
      rows: [
        row({
          interpretation: {
            ...row().interpretation,
            windows: [
              window({ pair: 1, decision: 'OPEN', endAt: null, minutes: null }),
              window({ pair: 2, test: true }),
              window({ pair: 3, mergedIntoPair: 1 }),
            ],
          },
        }),
      ],
    });

    const text = root.textContent ?? '';
    expect(text).toContain('Registrar como abierta');
    expect(text).toContain('Prueba');
    expect(text).toContain('Fusionada en el par 1');
    expect(text).toContain('abierta');
  });
});
