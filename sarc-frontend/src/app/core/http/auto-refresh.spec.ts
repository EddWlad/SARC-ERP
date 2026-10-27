import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { AUTO_REFRESH_MS, HUMAN_IDLE_MS, refreshWhileVisible } from './auto-refresh';

describe('refreshWhileVisible', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function start(reload: () => void): void {
    TestBed.runInInjectionContext(() => refreshWhileVisible(reload));
  }

  it('refreshes while the person interacts and stops once idle so the session can expire', () => {
    // RF-001.10: las consultas automaticas no deben mantener viva una sesion sin uso.
    const reload = vi.fn();
    start(reload);

    vi.advanceTimersByTime(AUTO_REFRESH_MS);
    expect(reload).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(HUMAN_IDLE_MS);
    reload.mockClear();
    vi.advanceTimersByTime(AUTO_REFRESH_MS * 3);
    expect(reload).not.toHaveBeenCalled();

    TestBed.inject(DOCUMENT).dispatchEvent(new KeyboardEvent('keydown'));
    vi.advanceTimersByTime(AUTO_REFRESH_MS);
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
