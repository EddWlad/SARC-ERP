import {
  HttpErrorResponse,
  HttpHeaders,
  HttpResponse,
  provideHttpClient,
} from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { FileSaver } from '../../../core/files/file-saver';
import { IssuedReportsApi } from '../data-access/issued-reports-api';
import { IssuedReport, kindLabel, REPORT_KINDS } from '../domain/issued-report';
import { IssuedReportsFacade } from './issued-reports-facade';

describe('IssuedReportsFacade', () => {
  function setup(api: Partial<IssuedReportsApi>) {
    const files = { save: vi.fn(), download: vi.fn().mockResolvedValue(undefined) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        IssuedReportsFacade,
        { provide: FileSaver, useValue: files },
        {
          provide: IssuedReportsApi,
          useValue: { listRequest: () => ({ url: '/qa' }), ...api },
        },
      ],
    });
    return { facade: TestBed.inject(IssuedReportsFacade), files };
  }

  it('lists uptime reports with their own label and filter', () => {
    const { facade } = setup({});

    expect(kindLabel('UPTIME')).toBe('Uptime');
    expect(kindLabel('INTERMITTENCY')).toBe('Intermitencias');
    expect(REPORT_KINDS).toContain('UPTIME');
    expect(REPORT_KINDS).toContain('INTERMITTENCY');
    facade.filterKind('UPTIME');
    expect(facade.kind()).toBe('UPTIME');
  });

  it('downloads the archived bytes through the authenticated binary endpoint', async () => {
    const download = vi.fn().mockReturnValue(of(new HttpResponse({ body: new Blob(['QA']) })));
    const { facade, files } = setup({ download });
    await facade.download({ id: 'r', fileName: 'inventario.pdf' } as IssuedReport);
    expect(download).toHaveBeenCalledWith('r');
    expect(files.download.mock.calls[0][1]).toBe('inventario.pdf');
  });

  it('returns the identity of the new issue from its own headers', async () => {
    const headers = new HttpHeaders({
      'X-Report-Id': 'nuevo',
      'X-Report-Cut-Off': '2026-10-31T00:00:00Z',
    });
    const response = new HttpResponse({ body: new Blob(['QA']), headers });
    const generateConsumptionReport = vi.fn().mockReturnValue(of(response));
    const { facade, files } = setup({ generateConsumptionReport });
    const criteria = { from: '2026-10-01', to: '2026-11-01', timeZone: 'America/Guayaquil' };
    await expect(facade.generateConsumptionReport(criteria, 'PDF')).resolves.toEqual({
      id: 'nuevo',
      cutOff: '2026-10-31T00:00:00Z',
    });
    expect(files.save).toHaveBeenCalledWith(response, 'consumo.pdf');
  });

  it('leaves a failed issue for the dialog and saves nothing', async () => {
    const generateConsumptionReport = vi
      .fn()
      .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422 })));
    const { facade, files } = setup({ generateConsumptionReport });
    await expect(
      facade.generateConsumptionReport({ from: 'a', to: 'b', timeZone: 'z' }, 'XLSX'),
    ).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(files.save).not.toHaveBeenCalled();
  });

  it('does not query equipment for a too short text', async () => {
    const equipmentOptions = vi.fn();
    const { facade } = setup({ equipmentOptions });
    await expect(facade.searchEquipment(' a ')).resolves.toEqual([]);
    expect(equipmentOptions).not.toHaveBeenCalled();
  });
});
