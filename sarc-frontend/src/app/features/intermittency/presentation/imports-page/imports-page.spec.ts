import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { IntermittencyImportHistory } from '../../application/import-history';
import { batch } from '../../testing/import-fixtures';
import { IntermittencyImportsPage } from './imports-page';

describe('IntermittencyImportsPage', () => {
  const history = {
    batches: signal([batch()]),
    totalElements: signal(1),
    pageIndex: signal(0),
    pageSize: signal(20),
    loading: signal(false),
    failed: signal(false),
    reload: vi.fn(),
    changePage: vi.fn(),
    upload: vi.fn(),
    paste: vi.fn(),
  };
  const dialog = { open: vi.fn() };

  function render() {
    TestBed.configureTestingModule({
      imports: [IntermittencyImportsPage],
      providers: [provideRouter([])],
    });
    TestBed.overrideComponent(IntermittencyImportsPage, {
      set: { providers: [{ provide: IntermittencyImportHistory, useValue: history }] },
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(IntermittencyImportsPage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('lista los lotes con su archivo, estado y conteos', () => {
    const { root } = render();

    const text = root.textContent ?? '';
    expect(text).toContain('registro.xlsx');
    expect(text).toContain('Por revisar');
    expect(text).toContain('2 en conflicto');
    expect(text).toContain('5 inválidas');
    expect(text).toContain('1 por vincular');
  });

  it('sin lotes muestra el estado vacio', () => {
    history.batches.set([]);
    const { root } = render();

    expect(root.textContent).toContain('Aún no hay importaciones de intermitencias');
    history.batches.set([batch()]);
  });

  it('una nueva importacion abre el dialogo y lleva a la vista previa del lote creado', async () => {
    const { fixture } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    dialog.open.mockReturnValue({ afterClosed: () => of(batch({ id: 'nuevo' })) });

    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidate) => candidate.textContent?.includes('Nueva importación'));
    button!.click();
    await fixture.whenStable();

    expect(dialog.open).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/intermitencias/importaciones', 'nuevo']);
  });
});
