import { describe, expect, it, vi } from 'vitest';

import { TicketVisitResponse } from '../../../../core/api/generated';
import {
  errors,
  hasField,
  openDialog,
  submitDialog,
  typeInto,
} from '../../testing/dialog-fixtures';
import { VisitDialog } from './visit-dialog';

function visit(overrides: Partial<TicketVisitResponse> = {}): TicketVisitResponse {
  return {
    id: 'v1',
    episodeOrdinal: 1,
    providerName: 'Proveedor Uno',
    stage: 'REQUESTED',
    createdAt: '2026-11-21T15:00:00Z',
    updatedAt: '2026-11-21T15:00:00Z',
    ...overrides,
  };
}

const providers = vi.fn().mockResolvedValue(['Otro Taller', 'Proveedor Uno']);

describe('VisitDialog', () => {
  it('solicitar una visita no pide etapa y sugiere los proveedores ya usados', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const harness = await openDialog(VisitDialog, { providers, save });

    expect(hasField(harness.element, 'Nueva etapa')).toBe(false);
    const options = Array.from(harness.element.querySelectorAll('datalist option')).map((o) =>
      o.getAttribute('value'),
    );
    expect(options).toEqual(['Otro Taller', 'Proveedor Uno']);

    typeInto(harness.element, 'Proveedor', 'Taller Nuevo');
    await submitDialog(harness);

    expect(save).toHaveBeenCalledWith({
      occurredAt: null,
      note: null,
      providerName: 'Taller Nuevo',
    });
    expect(harness.close).toHaveBeenCalledWith(true);
  });

  it('si las sugerencias no cargan, el proveedor se escribe igual', async () => {
    const harness = await openDialog(VisitDialog, {
      providers: vi.fn().mockRejectedValue(new Error('sin red')),
      save: vi.fn(),
    });
    expect(harness.element.querySelectorAll('datalist option')).toHaveLength(0);
    expect(hasField(harness.element, 'Proveedor')).toBe(true);
  });

  it('cambiar de etapa solo ofrece las transiciones permitidas', async () => {
    const harness = await openDialog(VisitDialog, { visit: visit(), providers, save: vi.fn() });

    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    const labels = Array.from(document.querySelectorAll('mat-option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(labels).toEqual(['Asignada', 'Cancelada', 'Aplazada']);
  });

  it('una visita aplazada solo vuelve a asignarse o confirmarse, sin pedir una decision', async () => {
    const harness = await openDialog(VisitDialog, {
      visit: visit({ stage: 'POSTPONED' }),
      providers,
      save: vi.fn(),
    });
    expect(hasField(harness.element, 'Quién decidió')).toBe(false);
    (harness.element.querySelector('mat-select') as HTMLElement).click();
    harness.fixture.detectChanges();
    expect(
      Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent?.trim()),
    ).toEqual(['Asignada', 'Confirmada']);
  });

  it('cancelar exige quien decidio, la evidencia y la condicion de nueva escalada', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const requested = await openDialog(VisitDialog, { visit: visit(), providers, save });
    (requested.element.querySelector('mat-select') as HTMLElement).click();
    requested.fixture.detectChanges();
    (
      Array.from(document.querySelectorAll('mat-option')).find((o) =>
        o.textContent?.includes('Cancelada'),
      ) as HTMLElement
    ).click();
    requested.fixture.detectChanges();
    await requested.fixture.whenStable();

    expect(hasField(requested.element, 'Quién decidió')).toBe(true);
    await submitDialog(requested);
    expect(save).not.toHaveBeenCalled();
    expect(errors(requested.element)).toEqual(
      expect.arrayContaining([
        'Indique quién decidió',
        'Indique la evidencia de la decisión',
        'Indique la condición para volver a escalar',
      ]),
    );

    typeInto(requested.element, 'Quién decidió', 'Supervisión');
    typeInto(requested.element, 'Evidencia de la decisión', 'Correo del proveedor');
    typeInto(requested.element, 'Condición para volver a escalar', 'Si reaparece la falla');
    await submitDialog(requested);
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({
        stage: 'CANCELLED',
        cancelDecidedBy: 'Supervisión',
        cancelEvidenceNote: 'Correo del proveedor',
        reescalationCondition: 'Si reaparece la falla',
      }),
    );
  });

  it('una visita en etapa final no ofrece cambiar y no permite enviar', async () => {
    const harness = await openDialog(VisitDialog, {
      visit: visit({ stage: 'REPORT_RECEIVED' }),
      providers,
      save: vi.fn(),
    });
    const submit = harness.element.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });
});
