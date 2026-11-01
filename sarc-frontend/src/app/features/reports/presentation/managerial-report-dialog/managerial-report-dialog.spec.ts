import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { IssuedReportsFacade } from '../../application/issued-reports-facade';
import { IssuedReport } from '../../domain/issued-report';
import { ManagerialReportDialog } from './managerial-report-dialog';

describe('ManagerialReportDialog', () => {
  async function setup(report: Partial<IssuedReport>) {
    const close = vi.fn();
    const facade = {
      generateConsumptionReport: vi.fn().mockResolvedValue({ id: 'n', cutOff: 'c' }),
      findEquipment: vi.fn().mockResolvedValue({ id: 'e', name: 'Equipo QA', assignedCode: 'Q1' }),
      searchEquipment: vi.fn().mockResolvedValue([]),
    };
    TestBed.configureTestingModule({
      imports: [ManagerialReportDialog],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: { report: { format: 'PDF', scope: 'Legado QA', ...report } },
        },
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
        },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: IssuedReportsFacade, useValue: facade },
      ],
    });
    const fixture = TestBed.createComponent(ManagerialReportDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, dialog: fixture.componentInstance, facade, close };
  }

  it('requires a confirmed period for a legacy original and never parses its scope', async () => {
    const { fixture, dialog, facade } = await setup({ scope: 'Periodo 01/10/2026 al 31/10/2026' });
    expect(dialog.model().from).toBe('');
    expect(fixture.nativeElement.textContent).toContain('no registró sus filtros');
    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    expect(facade.generateConsumptionReport).not.toHaveBeenCalled();
  });

  it('preloads structured criteria and converts the inclusive end back to the exclusive one', async () => {
    const { fixture, dialog, facade, close } = await setup({
      criteria: {
        from: '2026-10-01',
        to: '2026-11-01',
        network: 'JAC',
        equipmentId: 'e',
        timeZone: 'America/Guayaquil',
      },
    });
    expect(dialog.model().to).toBe('2026-10-31');
    expect(facade.findEquipment).toHaveBeenCalledWith('e');
    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    expect(facade.generateConsumptionReport.mock.calls[0][0]).toMatchObject({
      from: '2026-10-01',
      to: '2026-11-01',
      network: 'JAC',
      equipmentId: 'e',
    });
    expect(close).toHaveBeenCalledWith({ id: 'n', cutOff: 'c' });
  });

  it('does not issue with equipment text that was never selected', async () => {
    const { fixture, dialog, facade } = await setup({
      criteria: { from: '2026-10-01', to: '2026-11-01', timeZone: 'America/Guayaquil' },
    });
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      'input[matautocomplete], input[aria-autocomplete]',
    ) as HTMLInputElement;
    input.value = 'Equipo escrito';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();

    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(facade.generateConsumptionReport).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Elija un equipo');
  });

  it('rejects an inverted or longer than 366 days period', async () => {
    const { fixture, dialog, facade } = await setup({});
    for (const [from, to] of [
      ['2026-10-02', '2026-10-01'],
      ['2025-01-01', '2026-01-02'],
    ]) {
      dialog.model.update((value) => ({ ...value, from, to }));
      dialog.onSubmit(new Event('submit'));
      await fixture.whenStable();
    }
    expect(facade.generateConsumptionReport).not.toHaveBeenCalled();
  });

  it('keeps the dialog open with the backend message when the issue fails', async () => {
    const { fixture, dialog, facade, close } = await setup({});
    facade.generateConsumptionReport.mockRejectedValue({
      status: 422,
      code: 'REPORT_TOO_LARGE',
      detail: 'Acorte el periodo',
      fieldErrors: [],
      properties: {},
    });
    dialog.model.update((value) => ({ ...value, from: '2026-10-01', to: '2026-10-31' }));
    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    expect(close).not.toHaveBeenCalled();
  });
});
