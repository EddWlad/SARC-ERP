import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { TicketCommunicationResponse } from '../../../../core/api/generated';
import { CommunicationsPanel } from './communications-panel';

function item(overrides: Partial<TicketCommunicationResponse> = {}): TicketCommunicationResponse {
  return {
    id: 'c1',
    channel: 'KIA_ELECTROLINERAS',
    kind: 'INITIAL',
    sentAt: '2026-11-21T15:30:00Z',
    retroactive: false,
    recordedByName: 'Ana Analista',
    recordedAt: '2026-11-21T15:31:00Z',
    annulled: false,
    ...overrides,
  };
}

function render(communications: TicketCommunicationResponse[]): HTMLElement {
  const fixture = TestBed.createComponent(CommunicationsPanel);
  fixture.componentRef.setInput('communications', communications);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('CommunicationsPanel', () => {
  it('sin envios explica que copiar el mensaje no lo registra', () => {
    const text = render([]).textContent?.replace(/\s+/g, ' ') ?? '';
    expect(text).toContain('Todavía no se registró ningún envío');
    expect(text).toContain('Copiar el mensaje no lo registra');
  });

  it('muestra el tipo, el canal, la hora real y quien lo registro', () => {
    const text = render([item()]).textContent?.replace(/\s+/g, ' ') ?? '';
    expect(text).toContain('Aviso inicial');
    expect(text).toContain('Grupo KIA electrolineras');
    expect(text).toContain('enviado 2026-11-21 10:30');
    expect(text).toContain('registrado por Ana Analista');
  });

  it('un envio retroactivo muestra su justificacion y uno anulado sigue visible, tachado y con motivo', () => {
    const element = render([
      item({
        retroactive: true,
        justification: 'Se avisó por llamada',
        channel: 'OTHER',
        channelDetail: 'Llamada',
      }),
      item({ id: 'c2', annulled: true, annulReason: 'Ticket equivocado' }),
    ]);
    const text = element.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(text).toContain('retroactivo');
    expect(text).toContain('Justificación: Se avisó por llamada');
    expect(text).toContain('Otro: Llamada');
    expect(text).toContain('anulado: Ticket equivocado');
    expect(element.querySelectorAll('li.annulled')).toHaveLength(1);
  });
});
