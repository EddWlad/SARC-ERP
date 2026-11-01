import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { SessionStore } from '../../core/auth/session-store';
import { Shell } from './shell';

describe('Shell', () => {
  async function setup(matches: boolean) {
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        {
          provide: SessionStore,
          useValue: { user: signal(null), hasAnyRole: () => true, logout: vi.fn() },
        },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches }) } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
    document.body.append(fixture.nativeElement);
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  it('skips to the content without following the anchor', async () => {
    const { host } = await setup(false);
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    (host.querySelector('.skip-link') as HTMLElement).dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(host.querySelector('main'));
  });

  it('returns focus to the menu button when the drawer leaves it on the body', async () => {
    const { fixture, host } = await setup(true);
    (document.activeElement as HTMLElement | null)?.blur();
    (fixture.componentInstance as unknown as { restoreMenuFocus(): void }).restoreMenuFocus();

    expect(document.activeElement).toBe(host.querySelector('[aria-label="Abrir menú"]'));
  });
});
