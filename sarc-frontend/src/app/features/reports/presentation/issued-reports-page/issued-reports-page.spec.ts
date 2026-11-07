import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Notifier } from '../../../../core/notifications/notifier';
import { IssuedReportsFacade } from '../../application/issued-reports-facade';
import { IssuedReport } from '../../domain/issued-report';
import { IssuedReportsPage } from './issued-reports-page';

const REPORT = {
  id: 'r',
  kind: 'CONSUMPTION',
  format: 'PDF',
  fileName: 'consumo.pdf',
  scope: 'Periodo QA',
  checksum: 'a'.repeat(64),
  sizeBytes: 2048,
  archived: true,
  issuedAt: '2026-10-01T15:00:00Z',
  canDownload: true,
  canIssueManagerial: false,
} as IssuedReport;

describe('IssuedReportsPage', () => {
  function setup(report: IssuedReport) {
    const facade = {
      kind: signal(''),
      reports: signal([report]),
      loading: signal(false),
      failed: signal(false),
      totalElements: signal(1),
      pageIndex: signal(0),
      pageSize: signal(20),
      download: vi.fn().mockResolvedValue(undefined),
      reload: vi.fn(),
    };
    const notifier = { success: vi.fn(), error: vi.fn() };
    const dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => of(undefined) }) };
    TestBed.configureTestingModule({
      imports: [IssuedReportsPage],
      providers: [
        { provide: Notifier, useValue: notifier },
        { provide: MatDialog, useValue: dialog },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: false }) } },
      ],
    });
    TestBed.overrideComponent(IssuedReportsPage, {
      set: { providers: [{ provide: IssuedReportsFacade, useValue: facade }] },
    });
    const fixture = TestBed.createComponent(IssuedReportsPage);
    fixture.detectChanges();
    const button = (text: string) =>
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((item) =>
        item.textContent?.includes(text),
      );
    return { fixture, facade, notifier, dialog, button };
  }

  it('downloads an authorized copy and never opens the issue flow by itself', async () => {
    const { fixture, facade, dialog, button } = setup(REPORT);
    button('Descargar')?.click();
    await fixture.whenStable();
    expect(facade.download).toHaveBeenCalledWith(REPORT);
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('hides the SHA-256 fingerprint until the person asks for it', () => {
    const { fixture, button } = setup(REPORT);
    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text()).not.toContain(REPORT.checksum);

    button('Ver huella')?.click();
    fixture.detectChanges();
    expect(text()).toContain(REPORT.checksum);

    button('Ocultar huella')?.click();
    fixture.detectChanges();
    expect(text()).not.toContain(REPORT.checksum);
  });

  it('explains a restricted original and offers a new issue instead of the download', () => {
    const { fixture, dialog, button } = setup({
      ...REPORT,
      canDownload: false,
      canIssueManagerial: true,
      accessReason: 'REPORT_OPERATIONAL_CONTENT_RESTRICTED',
    });
    expect(button('Descargar')).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('información operativa restringida');
    button('Emitir informe gerencial')?.click();
    expect(dialog.open).toHaveBeenCalledOnce();
  });

  it('refreshes the row after a 403 and does not issue automatically', async () => {
    const { fixture, facade, notifier, dialog, button } = setup(REPORT);
    facade.download.mockRejectedValue({
      code: 'REPORT_OPERATIONAL_CONTENT_RESTRICTED',
      detail: 'Restringido',
    });
    button('Descargar')?.click();
    await fixture.whenStable();
    expect(notifier.error).toHaveBeenCalledWith('Restringido');
    expect(facade.reload).toHaveBeenCalledOnce();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('states a copy that does not exist and shows the cut-off only when known', () => {
    const { fixture } = setup({
      ...REPORT,
      archived: false,
      canDownload: false,
      accessReason: 'ISSUED_REPORT_NOT_ARCHIVED',
    });
    expect(fixture.nativeElement.textContent).toContain('Sin copia');
    expect(fixture.nativeElement.textContent).not.toContain('Corte');
  });
});
