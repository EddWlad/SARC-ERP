import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Session } from '../../domain/consumption';
import { EnergyReviewDialog, EnergyReviewDialogData } from './energy-review-dialog';

describe('EnergyReviewDialog', () => {
  async function setup(canEdit = true) {
    const close = vi.fn();
    const data: EnergyReviewDialogData = {
      session: { id: 's', version: 7, externalSessionId: 'QA', energyKwh: 1500 } as Session,
      canEdit,
      history: vi.fn().mockResolvedValue([]),
      save: vi.fn().mockResolvedValue({ id: 's' }),
      stale: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [EnergyReviewDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
        },
      ],
    });
    const fixture = TestBed.createComponent(EnergyReviewDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, dialog: fixture.componentInstance, data, close };
  }

  it('lets readers scroll the justification with the keyboard', async () => {
    const reader = await setup(false);
    const content = (reader.fixture.nativeElement as HTMLElement).querySelector(
      'mat-dialog-content',
    );
    expect(content?.getAttribute('tabindex')).toBe('0');
  });

  it('rejects blank or long reasons and submits trimmed limits', async () => {
    const { fixture, dialog, data } = await setup();
    for (const reason of ['   ', 'x'.repeat(501)]) {
      dialog.model.set({ decision: 'APPROVED', justification: reason });
      dialog.onSubmit(new Event('submit'));
      await fixture.whenStable();
      expect(data.save).not.toHaveBeenCalled();
    }
    dialog.model.set({ decision: 'REJECTED', justification: ` ${'x'.repeat(500)} ` });
    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    expect(data.save).toHaveBeenCalledWith('REJECTED', 'x'.repeat(500));
  });

  it('does not save or close on an obsolete version and requires reopening', async () => {
    const { fixture, dialog, data, close } = await setup();
    vi.mocked(data.save).mockRejectedValue(
      new HttpErrorResponse({
        error: { code: 'CONCURRENT_MODIFICATION', detail: 'Recargue' },
        status: 409,
      }),
    );
    dialog.model.set({ decision: 'APPROVED', justification: 'Verificada' });
    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    expect(close).not.toHaveBeenCalled();
    expect(dialog.error()).not.toBe('');
    expect(dialog.stale()).toBe(true);
    expect(data.stale).toHaveBeenCalledOnce();
  });

  it('shows history without editing controls to readers', async () => {
    const { fixture, dialog, data } = await setup(false);
    expect(fixture.nativeElement.textContent).toContain('Justificación aprobada');
    expect(fixture.nativeElement.textContent).not.toContain('Historial de decisiones');
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
    dialog.onSubmit(new Event('submit'));
    expect(data.save).not.toHaveBeenCalled();
  });

  it('accepts a one-character reason', async () => {
    const { fixture, dialog, data } = await setup();
    dialog.model.set({ decision: 'APPROVED', justification: ' x ' });
    dialog.onSubmit(new Event('submit'));
    await fixture.whenStable();
    expect(data.save).toHaveBeenCalledWith('APPROVED', 'x');
  });
});
