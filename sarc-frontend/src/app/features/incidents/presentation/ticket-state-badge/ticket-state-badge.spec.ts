import { TestBed } from '@angular/core/testing';

import { TicketStateBadge } from './ticket-state-badge';

describe('TicketStateBadge', () => {
  function render(state: 'OPEN' | 'CLOSED'): HTMLElement {
    const fixture = TestBed.createComponent(TicketStateBadge);
    fixture.componentRef.setInput('state', state);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.badge') as HTMLElement;
  }

  it('muestra «Abierto» con texto y destacado', () => {
    const badge = render('OPEN');
    expect(badge.textContent?.trim()).toBe('Abierto');
    expect(badge.classList).toContain('badge-warn');
  });

  it('muestra «Cerrado» con texto y apagado', () => {
    const badge = render('CLOSED');
    expect(badge.textContent?.trim()).toBe('Cerrado');
    expect(badge.classList).toContain('badge-off');
  });
});
