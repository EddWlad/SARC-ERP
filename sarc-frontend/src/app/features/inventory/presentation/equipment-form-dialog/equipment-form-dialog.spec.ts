import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { EMPTY } from 'rxjs';

import { ConnectorTypesFacade } from '../../application/connector-types-facade';
import { Equipment } from '../../domain/equipment';
import { EquipmentFormDialog, EquipmentFormDialogData } from './equipment-form-dialog';

const KIA_POINT = {
  id: 'eq-1',
  network: 'KIA',
  type: 'CHARGING_POINT',
  brand: 'WALLBOX',
  name: 'Punto Quito',
  serial: 'SM-1',
  assignedCode: '123456',
  uidPuk: 'UID-1',
  usageStatus: 'FREE',
  adminStatus: 'INACTIVE',
  connectors: [
    {
      id: 'c-1',
      ordinal: 1,
      connectorTypeId: 'type2',
      connectorTypeCode: 'TYPE2',
      connectorTypeName: 'Tipo 2',
    },
  ],
  version: 4,
} as Equipment;

const JAC_STATION = { ...KIA_POINT, network: 'JAC', type: 'CHARGING_STATION' } as Equipment;

describe('EquipmentFormDialog', () => {
  let fixture: ComponentFixture<EquipmentFormDialog>;
  const save = vi.fn<EquipmentFormDialogData['save']>();
  const close = vi.fn();
  const navigate = vi.fn(() => Promise.resolve(true));

  async function open(data: Omit<EquipmentFormDialogData, 'save'>): Promise<void> {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [EquipmentFormDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { ...data, save } },
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
        },
        { provide: Router, useValue: { navigate } },
        {
          provide: ConnectorTypesFacade,
          useValue: {
            active: signal([{ id: 'type2', code: 'TYPE2', name: 'Tipo 2', status: 'ACTIVE' }]),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(EquipmentFormDialog);
    await fixture.whenStable();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function submit(): Promise<void> {
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('asks for the KIA code and usage status on a KIA point replacement', async () => {
    await open({ replaces: KIA_POINT });

    expect(text()).toContain('Código asignado');
    expect(text()).toContain('Estado de uso');
    await submit();

    expect(save).not.toHaveBeenCalled();
    expect(text()).toContain('Ingrese el código asignado');
    expect(text()).toContain('El UID/PUK es obligatorio en los puntos de carga');
  });

  it('uses the serial as code and hides the usage status on stations', async () => {
    await open({ replaces: JAC_STATION });

    expect(text()).toContain('El código asignado es la serie.');
    expect(text()).not.toContain('Estado de uso');
    expect(text()).toContain('Tipos de conector');
  });

  it.each(['', '   '])('allows FREE without code (%j) and submits absence', async (code) => {
    await open({ equipment: { ...KIA_POINT, assignedCode: null } });
    fixture.componentInstance['model'].update((value) => ({ ...value, assignedCode: code }));
    await fixture.whenStable();
    expect(fixture.componentInstance['equipmentForm']().valid()).toBe(true);
    await submit();
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ assignedCode: undefined }));
  });

  it.each(['BLOCKED', 'DISCONNECTED'] as const)('requires a code for %s', async (usageStatus) => {
    await open({ equipment: { ...KIA_POINT, assignedCode: null, usageStatus } });
    await submit();
    expect(save).not.toHaveBeenCalled();
    expect(text()).toContain('Ingrese el código asignado');
  });

  it.each(['012345', '12345', '1234567', '12A456'])(
    'rejects informed invalid code %s even FREE',
    async (assignedCode) => {
      await open({ equipment: { ...KIA_POINT, assignedCode } });
      await submit();
      expect(save).not.toHaveBeenCalled();
    },
  );

  it('retains the preloaded code while switching BLOCKED to FREE and back', async () => {
    await open({ equipment: { ...KIA_POINT, assignedCode: '654321', usageStatus: 'BLOCKED' } });
    for (const usageStatus of ['FREE', 'BLOCKED'] as const) {
      fixture.componentInstance['model'].update((value) => ({ ...value, usageStatus }));
      await fixture.whenStable();
      expect(fixture.componentInstance['model']().assignedCode).toBe('654321');
      expect(fixture.componentInstance['equipmentForm']().valid()).toBe(true);
    }
    await submit();
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({ assignedCode: '654321', usageStatus: 'BLOCKED' }),
    );
  });

  it('blocks FREE without code when switched to BLOCKED until a valid code is supplied', async () => {
    await open({ equipment: { ...KIA_POINT, assignedCode: null } });
    fixture.componentInstance['model'].update((value) => ({ ...value, usageStatus: 'BLOCKED' }));
    await submit();
    expect(save).not.toHaveBeenCalled();
    fixture.componentInstance['model'].update((value) => ({ ...value, assignedCode: '654321' }));
    await submit();
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({ assignedCode: '654321', usageStatus: 'BLOCKED' }),
    );
  });

  it('offers to open the equipment that already uses the identifiers', async () => {
    save.mockRejectedValue(
      new HttpErrorResponse({
        status: 409,
        error: {
          code: 'EQUIPMENT_DELETED_EXISTS',
          detail: 'Ya existe un equipo eliminado con esos identificadores',
          equipmentId: 'eq-9',
        },
      }),
    );
    await open({ equipment: KIA_POINT });

    await submit();
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({
        serial: 'SM-1',
        assignedCode: '123456',
        connectorTypeIds: ['type2'],
      }),
    );
    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((element) => element.textContent?.includes('Ir a recuperarlo'));
    button!.click();
    await fixture.whenStable();

    expect(close).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/inventario', 'eq-9']);
  });
});
