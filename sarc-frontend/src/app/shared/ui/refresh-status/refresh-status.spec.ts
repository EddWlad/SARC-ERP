import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RefreshStatus } from '../../../core/http/auto-refresh';
import { formatClock, RefreshStatusIndicator } from './refresh-status';

describe('RefreshStatusIndicator', () => {
  let fixture: ComponentFixture<RefreshStatusIndicator>;
  let status: RefreshStatus;

  const text = () =>
    (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ').trim();

  beforeEach(() => {
    status = new RefreshStatus();
    fixture = TestBed.createComponent(RefreshStatusIndicator);
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
  });

  it('formatea la hora en la zona operativa', () => {
    expect(formatClock(new Date('2026-11-21T20:05:00Z'))).toBe('15:05');
    expect(formatClock(new Date('2026-11-22T04:59:00Z'))).toBe('23:59');
  });

  it('no muestra nada antes de la primera actualizacion', () => {
    expect(text()).toBe('');
  });

  it('muestra la hora de la ultima actualizacion', async () => {
    status.markSuccess(new Date('2026-11-21T20:05:00Z'));
    fixture.detectChanges();
    expect(text()).toBe('Actualizado a las 15:05');
  });

  it('avisa con texto cuando se pierde la conexion y conserva la hora', () => {
    status.markSuccess(new Date('2026-11-21T20:05:00Z'));
    status.markFailure(new HttpErrorResponse({ status: 0 }));
    fixture.detectChanges();
    expect(text()).toContain('Actualizado a las 15:05');
    expect(text()).toContain('Sin conexión con el servidor');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="status"]')).not.toBeNull();
  });

  it('el aviso desaparece al reconectar', () => {
    status.markFailure(new HttpErrorResponse({ status: 503 }));
    fixture.detectChanges();
    expect(text()).toContain('Sin conexión');
    status.markSuccess(new Date('2026-11-21T20:06:00Z'));
    fixture.detectChanges();
    expect(text()).not.toContain('Sin conexión');
  });
});
