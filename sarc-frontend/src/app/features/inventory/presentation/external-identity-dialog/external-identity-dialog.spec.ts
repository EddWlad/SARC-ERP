import { TestBed } from '@angular/core/testing';
import { MatDialog, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ExternalIdentityDialog } from './external-identity-dialog';

describe('ExternalIdentityDialog', () => {
  it('lets the scope hint grow instead of overlapping the next field', async () => {
    TestBed.configureTestingModule({
      imports: [ExternalIdentityDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { save: vi.fn() } },
        {
          provide: MatDialogRef,
          useValue: { close: vi.fn(), backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
        },
        { provide: MatDialog, useValue: { open: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(ExternalIdentityDialog);
    fixture.detectChanges();
    await fixture.whenStable();

    const fields = (fixture.nativeElement as HTMLElement).querySelectorAll('mat-form-field');
    expect(fields).toHaveLength(4);
    for (const field of Array.from(fields)) {
      expect(field.querySelector('.mat-mdc-form-field-subscript-dynamic-size')).not.toBeNull();
    }
  });
});
