import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of, Subject } from 'rxjs';

import { guardUnsavedChanges } from './unsaved-changes';

describe('guardUnsavedChanges', () => {
  const close = vi.fn();
  const backdrop = new Subject<MouseEvent>();
  const keys = new Subject<KeyboardEvent>();
  const ref = {
    close,
    disableClose: false,
    backdropClick: () => backdrop,
    keydownEvents: () => keys,
  };
  let answer: boolean | undefined;
  const open = vi.fn(() => ({ afterClosed: () => of(answer) }));

  beforeEach(() => {
    close.mockClear();
    open.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: MatDialogRef, useValue: ref },
        { provide: MatDialog, useValue: { open } },
      ],
    });
  });

  function guard(dirty: boolean) {
    return TestBed.runInInjectionContext(() => guardUnsavedChanges(() => dirty));
  }

  it('closes at once when nothing changed', async () => {
    await guard(false).cancel();

    expect(ref.disableClose).toBe(true);
    expect(open).not.toHaveBeenCalled();
    expect(close).toHaveBeenCalled();
  });

  it('keeps the form open unless the user confirms discarding the changes', async () => {
    const unsaved = guard(true);

    answer = false;
    await unsaved.cancel();
    expect(close).not.toHaveBeenCalled();

    answer = true;
    keys.next(new KeyboardEvent('keydown', { key: 'Escape' }));
    await Promise.resolve();
    expect(open).toHaveBeenCalledTimes(2);
    expect(close).toHaveBeenCalled();
  });
});
