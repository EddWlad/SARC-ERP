import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';

import { Session, SessionDetail } from '../../domain/consumption';
import {
  SessionAction,
  SessionActionDialog,
  SessionActionDialogData,
} from './session-action-dialog';

const SESSION = {
  id: 's-1211',
  externalSessionId: '1211',
  source: 'ENERLINK',
  startedAt: '2026-11-11T00:30:00Z',
  endedAt: '2026-11-11T02:10:05Z',
  sourceUtcOffsetMinutes: 0,
  sourceEndUtcOffsetMinutes: -300,
  startDate: '2026-11-11',
  energyKwh: 8,
  state: 'VALIDATED',
  canApproveSource: false,
  revision: 1,
  version: 4,
  importBatchId: 'b',
} as Session;

function setUp(action: SessionAction, overrides: Partial<SessionActionDialogData> = {}) {
  const close = vi.fn();
  const data: SessionActionDialogData = {
    action,
    session: SESSION,
    detail: vi.fn().mockResolvedValue({
      session: SESSION,
      revisions: [
        {
          revision: 1,
          kind: 'EDIT',
          reason: 'Lectura corregida',
          supersededAt: '2026-11-12T15:00:00Z',
          startedAt: '2026-11-11T00:30:00Z',
          sourceUtcOffsetMinutes: 0,
          energyKwh: 7,
          state: 'VALIDATED',
        },
      ],
    } as SessionDetail),
    edit: vi.fn().mockResolvedValue({ ...SESSION, energyKwh: 9 }),
    delete: vi.fn().mockResolvedValue({ ...SESSION, deletedAt: '2026-11-12T15:00:00Z' }),
    stale: vi.fn(),
    ...overrides,
  };
  TestBed.configureTestingModule({
    imports: [SessionActionDialog],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      {
        provide: MatDialogRef,
        useValue: {
          close,
          disableClose: false,
          backdropClick: () => new Subject<MouseEvent>(),
          keydownEvents: () => new Subject<KeyboardEvent>(),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(SessionActionDialog);
  fixture.detectChanges();
  return { fixture, data, close, dialog: fixture.componentInstance };
}

function setReason(dialog: SessionActionDialog, reason: string) {
  dialog.model.update((model) => ({ ...model, reason }));
}

describe('SessionActionDialog', () => {
  it('shows the source times, the attribution date and the previous versions', async () => {
    const { fixture } = setUp('view');
    await fixture.whenStable();
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

    expect(text).toContain('11/11/2026 00:30:00 (UTC+00:00)');
    expect(text).toContain('10/11/2026 21:10:05 (UTC-05:00)');
    expect(text).toMatch(/Fecha de inicio según la fuente\s*11\/11\/2026/);
    expect(text).toContain('Edición');
    expect(text).toContain('Lectura corregida');
    expect((fixture.nativeElement as HTMLElement).querySelector('textarea')).toBeNull();
  });

  it('opens the edit form in source time and sends only the changed fields with the reason', async () => {
    const { dialog, data, close } = setUp('edit');
    expect(dialog.model().start).toBe('2026-11-11T00:30:00');
    expect(dialog.model().end).toBe('2026-11-10T21:10:05');

    dialog.model.update((model) => ({ ...model, energy: '9', reason: ' Lectura del proveedor ' }));
    dialog.onSubmit(new Event('submit'));
    await vi.waitFor(() => expect(close).toHaveBeenCalled());

    expect(data.edit).toHaveBeenCalledWith({ energyKwh: 9 }, 'Lectura del proveedor');
  });

  it('keeps each end in its own source offset and leaves an emptied value as not reported', async () => {
    const { dialog, data, close } = setUp('edit');
    dialog.model.update((model) => ({
      ...model,
      start: '2026-11-11T00:40:00',
      end: '',
      energy: '',
      reason: 'Corte de energía',
    }));
    dialog.onSubmit(new Event('submit'));
    await vi.waitFor(() => expect(close).toHaveBeenCalled());

    expect(data.edit).toHaveBeenCalledWith(
      { startedAt: '2026-11-11T00:40:00+00:00', endedAt: null, energyKwh: null },
      'Corte de energía',
    );
  });

  it('requires a reason and never saves without one', async () => {
    const { dialog, data } = setUp('delete');
    dialog.onSubmit(new Event('submit'));
    await Promise.resolve();

    expect(
      dialog.actionForm
        .reason()
        .errors()
        .map((error) => error.message),
    ).toContain('Ingrese el motivo');
    expect(data.delete).not.toHaveBeenCalled();
  });

  it('deletes with the reason after the explicit confirmation of the dialog', async () => {
    const { dialog, data, close } = setUp('delete');
    setReason(dialog, 'Sesión duplicada');
    dialog.onSubmit(new Event('submit'));
    await vi.waitFor(() => expect(close).toHaveBeenCalled());

    expect(data.delete).toHaveBeenCalledWith('Sesión duplicada');
  });

  it('keeps what was written on a conflict and offers to reload instead of retrying', async () => {
    const conflict = new HttpErrorResponse({
      status: 409,
      error: { code: 'CONCURRENT_MODIFICATION', detail: 'La sesión cambió; recargue' },
    });
    const { dialog, data, close, fixture } = setUp('edit', {
      edit: vi.fn().mockRejectedValue(conflict),
    });
    dialog.model.update((model) => ({ ...model, energy: '9', reason: 'Lectura' }));
    dialog.onSubmit(new Event('submit'));
    await vi.waitFor(() => expect(dialog.stale()).toBe(true));
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(dialog.model().energy).toBe('9');
    const reload = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((button) => button.textContent?.includes('Recargar sesiones'));
    reload?.click();
    expect(data.stale).toHaveBeenCalled();
    expect(close).toHaveBeenCalledWith();
  });
});
