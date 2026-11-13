import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ImportRowActions } from '../../application/import-row-actions';
import { IntermittencyImportRow } from '../../domain/intermittency-import';
import { row, window } from '../../testing/import-fixtures';
import { ImportRowEditor } from './import-row-editor';

describe('ImportRowEditor', () => {
  const actions = {
    editRow: vi.fn(),
    linkRow: vi.fn(),
    decideRow: vi.fn(),
  } satisfies ImportRowActions;
  const close = vi.fn();

  interface Editor {
    date: { set(value: string): void };
    code: { set(value: string): void };
    comment: { set(value: string): void };
    rowTest: { set(value: boolean): void };
    patchWindow(pair: number, patch: object): void;
    addNew(): void;
    patchNew(key: number, patch: object): void;
    hasChanges(): boolean;
    onSubmit(event: Event): Promise<void>;
    error(): string;
  }

  function render(current: IntermittencyImportRow = row()) {
    actions.editRow.mockReset().mockResolvedValue({ row: current, batch: {} });
    close.mockReset();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ImportRowEditor],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { row: current, actions } },
        {
          provide: MatDialogRef,
          useValue: {
            close,
            disableClose: false,
            backdropClick: () => ({ subscribe: vi.fn() }),
            keydownEvents: () => ({ subscribe: vi.fn() }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ImportRowEditor);
    fixture.detectChanges();
    return {
      fixture,
      editor: fixture.componentInstance as unknown as Editor,
      root: fixture.nativeElement as HTMLElement,
    };
  }

  const submit = () => new Event('submit');

  it('sin cambios no deja guardar y muestra el original sin modificarlo', () => {
    const { editor, root } = render();

    expect(editor.hasChanges()).toBe(false);
    expect(root.textContent).toContain('Corregir la fila 3');
    expect(root.textContent).toContain('Fecha 2026-10-05');
    expect(root.textContent).toContain('Código cargador 900001');
  });

  it('marcar el dia siguiente envia solo ese cambio', async () => {
    const invalid = row({
      classification: 'INVALID',
      interpretation: {
        ...row().interpretation,
        windows: [
          window({
            startText: '17:00',
            endText: '07:05',
            startAt: '2026-10-04T17:00:00-05:00',
            endAt: '2026-10-04T07:05:00-05:00',
            minutes: null,
            issues: [{ code: 'END_BEFORE_START', message: 'El fin es anterior o igual al inicio' }],
          }),
        ],
      },
    });
    const { editor, fixture, root } = render(invalid);
    expect(root.textContent).toContain('El fin es anterior o igual al inicio');

    editor.patchWindow(1, { endsNextDay: true });
    fixture.detectChanges();
    await editor.onSubmit(submit());

    expect(actions.editRow).toHaveBeenCalledWith(invalid, {
      edits: [{ target: 'window', pair: 1, endsNextDay: true }],
      addWindows: [],
      removePairs: [],
    });
    expect(close).toHaveBeenCalled();
  });

  it('una caida de varios dias se corrige con el fin completo, con fecha, hora y desfase', async () => {
    const { editor } = render();

    editor.patchWindow(1, { end: '2026-10-08T09:58' });
    await editor.onSubmit(submit());

    expect(actions.editRow.mock.calls[0][1].edits).toEqual([
      { target: 'window', pair: 1, endAt: '2026-10-08T09:58:00-05:00' },
    ]);
  });

  it('corrige la fecha, el codigo y el comentario, y marca una ventana como prueba', async () => {
    const { editor } = render();

    editor.date.set('2026-10-06');
    editor.code.set('900002');
    editor.comment.set('Corte del proveedor');
    editor.patchWindow(1, { test: true });
    await editor.onSubmit(submit());

    expect(actions.editRow.mock.calls[0][1].edits).toEqual([
      { target: 'date', value: '2026-10-06' },
      { target: 'code', value: '900002' },
      { target: 'comment', value: 'Corte del proveedor' },
      { target: 'test', pair: 1, value: 'true' },
    ]);
  });

  it('marcar toda la fila como prueba se envia una sola vez', async () => {
    const { editor } = render();

    editor.rowTest.set(true);
    editor.patchWindow(1, { test: true });
    await editor.onSubmit(submit());

    expect(actions.editRow.mock.calls[0][1].edits).toEqual([{ target: 'test', value: 'true' }]);
  });

  it('agrega y omite pares', async () => {
    const { editor } = render();

    editor.patchWindow(1, { removed: true });
    editor.addNew();
    editor.patchNew(1, { start: '2026-10-05T14:00', end: '2026-10-05T14:20' });
    editor.addNew();
    await editor.onSubmit(submit());

    expect(actions.editRow.mock.calls[0][1]).toEqual({
      edits: [],
      addWindows: [{ startAt: '2026-10-05T14:00:00-05:00', endAt: '2026-10-05T14:20:00-05:00' }],
      removePairs: [1],
    });
  });

  it('un error del servidor se muestra y conserva lo digitado', async () => {
    const { editor, fixture } = render();
    actions.editRow.mockRejectedValue(new Error('El fin debe ser posterior al inicio'));

    editor.comment.set('x');
    await editor.onSubmit(submit());
    fixture.detectChanges();

    expect(editor.error()).toBe('El fin debe ser posterior al inicio');
    expect(close).not.toHaveBeenCalled();
    expect(editor.hasChanges()).toBe(true);
  });
});
