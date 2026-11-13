import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ImportRowActions } from '../../application/import-row-actions';
import { row, window } from '../../testing/import-fixtures';
import { PointPicker } from '../point-picker/point-picker';
import { OPEN_TEST_REASON, resolutionOptions, RowDecisionPanel } from './row-decision-panel';

const issue = (code: string) => ({ code: code as never, message: code });

describe('resolutionOptions', () => {
  it('una ventana sin observaciones solo se puede omitir', () => {
    expect(resolutionOptions(window(), row()).map((option) => option.value)).toEqual(['OMIT']);
  });

  it('un par incompleto con inicio se puede abrir, y con la marca de prueba se deshabilita con su motivo', () => {
    const incomplete = window({ endAt: null, minutes: null, issues: [issue('INCOMPLETE_PAIR')] });

    const open = resolutionOptions(incomplete, row()).find((option) => option.value === 'OPEN');
    expect(open?.disabledReason).toBeUndefined();

    const marked = resolutionOptions({ ...incomplete, test: true }, row()).find(
      (option) => option.value === 'OPEN',
    );
    expect(marked?.disabledReason).toBe('Una ventana de prueba debe estar cerrada');
    expect(OPEN_TEST_REASON).toBe('Una ventana de prueba debe estar cerrada');
  });

  it('un par sin inicio no se puede abrir', () => {
    const noStart = window({ startAt: null, endAt: null, issues: [issue('INCOMPLETE_PAIR')] });

    expect(resolutionOptions(noStart, row()).map((option) => option.value)).not.toContain('OPEN');
  });

  it('un solapamiento con lo registrado ofrece conservar y, si es uno solo, reemplazar', () => {
    const one = window({ issues: [issue('OVERLAPS_EXISTING')], conflictWindowId: 'w1' });
    const several = window({ issues: [issue('OVERLAPS_EXISTING')] });

    expect(resolutionOptions(one, row()).map((option) => option.value)).toEqual([
      'KEEP_EXISTING',
      'REPLACE',
      'OMIT',
    ]);
    expect(resolutionOptions(several, row()).map((option) => option.value)).toEqual([
      'KEEP_EXISTING',
      'OMIT',
    ]);
  });

  it('solo se fusionan dos ventanas solapadas de la misma fila', () => {
    const overlapping = [
      window({ pair: 1, issues: [issue('OVERLAP_IN_BATCH')] }),
      window({ pair: 2, issues: [issue('OVERLAP_IN_BATCH')] }),
    ];
    const withBoth = row({ interpretation: { ...row().interpretation, windows: overlapping } });
    const alone = row({ interpretation: { ...row().interpretation, windows: [overlapping[0]] } });

    expect(resolutionOptions(overlapping[1], withBoth).map((option) => option.value)).toContain(
      'MERGE',
    );
    expect(resolutionOptions(overlapping[0], alone).map((option) => option.value)).not.toContain(
      'MERGE',
    );
  });

  it('una ventana ya conservada sigue ofreciendo reemplazar, para poder cambiar de idea', () => {
    const kept = window({ decision: 'KEEP_EXISTING' });

    expect(resolutionOptions(kept, row()).map((option) => option.value)).toEqual([
      'KEEP_EXISTING',
      'REPLACE',
      'OMIT',
    ]);
  });
});

describe('RowDecisionPanel', () => {
  const actions = {
    editRow: vi.fn(),
    linkRow: vi.fn(),
    decideRow: vi.fn(),
  } satisfies ImportRowActions;
  const close = vi.fn();

  function render(current = row(), resolving = false) {
    actions.decideRow.mockReset().mockResolvedValue({ row: current, batch: {} });
    actions.linkRow
      .mockReset()
      .mockResolvedValue({ row: { ...current, classification: 'NEW' }, batch: {} });
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [RowDecisionPanel],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { row: current, actions, resolving } },
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
    TestBed.overrideComponent(RowDecisionPanel, {
      remove: { imports: [PointPicker] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(RowDecisionPanel);
    fixture.detectChanges();
    const panel = fixture.componentInstance as unknown as {
      decision: { set(value: string): void };
      skipReason: { set(value: string): void };
      acknowledge: { set(value: boolean): void };
      setResolution(pair: number, resolution: string): void;
      setJustification(pair: number, text: string): void;
      linkTarget: { set(value: unknown): void };
      canSave(): boolean;
      save(): Promise<void>;
      link(): Promise<void>;
    };
    return { fixture, panel, root: fixture.nativeElement as HTMLElement };
  }

  it('omitir la fila exige el motivo', async () => {
    const { fixture, panel } = render();
    panel.decision.set('SKIP');
    fixture.detectChanges();
    expect(panel.canSave()).toBe(false);

    panel.skipReason.set('Dato mal tipeado en el Excel');
    expect(panel.canSave()).toBe(true);
    await panel.save();

    expect(actions.decideRow).toHaveBeenCalledWith(expect.anything(), {
      decision: 'SKIP',
      skipReason: 'Dato mal tipeado en el Excel',
      acknowledgeTotal: false,
      windows: [],
    });
    expect(close).toHaveBeenCalled();
  });

  it('reemplazar una ventana registrada exige la justificacion y la envia', async () => {
    const conflict = row({
      classification: 'CONFLICT',
      interpretation: {
        ...row().interpretation,
        windows: [
          window({
            issues: [issue('OVERLAPS_EXISTING')],
            conflictWindowId: 'w1',
            classification: 'CONFLICT',
          }),
        ],
      },
    });
    const { panel } = render(conflict);

    panel.setResolution(1, 'REPLACE');
    expect(panel.canSave()).toBe(false);
    panel.setJustification(1, 'La hora del archivo es la correcta');
    expect(panel.canSave()).toBe(true);
    await panel.save();

    expect(actions.decideRow).toHaveBeenCalledWith(expect.anything(), {
      decision: 'APPLY',
      acknowledgeTotal: false,
      windows: [
        { pair: 1, resolution: 'REPLACE', justification: 'La hora del archivo es la correcta' },
      ],
    });
  });

  it('acordar el total solo se ofrece, y se envia, cuando el total no coincide', async () => {
    const mismatch = row({
      classification: 'REVIEW',
      interpretation: {
        ...row().interpretation,
        fileTotalMinutes: 109,
        computedTotalMinutes: 83,
        rowIssues: [issue('TOTAL_MISMATCH')],
      },
    });
    const { fixture, panel, root } = render(mismatch);

    expect(root.textContent).toContain(
      'El archivo dice 1:49 (109 min); las ventanas suman 1:23 (83 min)',
    );
    panel.acknowledge.set(true);
    fixture.detectChanges();
    await panel.save();

    expect(actions.decideRow).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        acknowledgeTotal: true,
      }),
    );
  });

  it('un lote sin diferencia de total no muestra la seccion del total', () => {
    const { root } = render();

    expect(root.textContent).not.toContain('Total del archivo');
  });

  it('una fila por vincular muestra el selector de punto y la sugerencia del nombre', () => {
    const pending = row({
      classification: 'PENDING_LINK',
      interpretation: {
        ...row().interpretation,
        name: 'Cargador de prueba',
        rowIssues: [issue('UNKNOWN_CODE')],
      },
    });
    const { root } = render(pending);

    expect(root.textContent).toContain('Cargador de prueba');
    expect(root.textContent).toContain('solo una sugerencia');
    expect(root.querySelector('sarc-point-picker')).not.toBeNull();
  });

  it('vincular llama a la accion con el punto elegido y renueva la fila', async () => {
    const pending = row({ classification: 'PENDING_LINK' });
    const { panel } = render(pending);

    panel.linkTarget.set({ id: 'p1', name: 'Punto', adminStatus: 'ACTIVE' });
    await panel.link();

    expect(actions.linkRow).toHaveBeenCalledWith(expect.anything(), 'p1');
  });

  it('dejar pendiente solo se ofrece a una fila por vincular o en conflicto', () => {
    const invalid = render(row({ classification: 'INVALID' })).root;
    expect(invalid.textContent).not.toContain('Dejarla pendiente');
    TestBed.resetTestingModule();
    const pending = render(row({ classification: 'PENDING_LINK' })).root;
    expect(pending.textContent).toContain('Dejarla pendiente');
  });

  describe('al resolver una fila pendiente de un lote confirmado', () => {
    const pendingRow = () =>
      row({ classification: 'PENDING_LINK', decision: 'KEEP_PENDING', equipmentId: null });

    it('no ofrece dejarla pendiente y parte de incorporarla', async () => {
      const { panel, root } = render(pendingRow(), true);

      expect(root.textContent).not.toContain('Dejarla pendiente');
      expect(root.textContent).toContain('Incorporar ahora');
      await panel.save();

      expect(actions.decideRow).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ decision: 'APPLY' }),
      );
    });

    it('fuera de este modo una fila pendiente conserva su decision de dejarla pendiente', async () => {
      const { panel } = render(pendingRow());

      await panel.save();

      expect(actions.decideRow).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ decision: 'KEEP_PENDING' }),
      );
    });

    it('una fila vinculada deja de pedir el punto', () => {
      const { root } = render(row({ classification: 'PENDING_LINK', equipmentId: 'p7' }), true);

      expect(root.querySelector('[aria-label="Vincular a un punto"]')).toBeNull();
    });
  });
});
