import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { KiaPointResponse } from '../../../../core/api/generated';
import { PointPicker } from '../point-picker/point-picker';
import { PointTestActions, PointTestDialog } from './point-test-dialog';

const POINT: KiaPointResponse = {
  id: 'p1',
  name: 'Punto A',
  assignedCode: '900001',
  adminStatus: 'ACTIVE',
};

describe('PointTestDialog', () => {
  const close = vi.fn();
  const actions = {
    previewPointTest: vi.fn(),
    pointTest: vi.fn(),
  } satisfies PointTestActions;

  function render() {
    close.mockReset();
    actions.previewPointTest.mockReset().mockResolvedValue({ willMark: 7, openNotMarked: 2 });
    actions.pointTest
      .mockReset()
      .mockResolvedValue({ affectedWindows: 7, openWindowsNotMarked: 2 });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => of(), keydownEvents: () => of() },
        },
        { provide: MAT_DIALOG_DATA, useValue: { actions } },
      ],
    });
    const fixture = TestBed.createComponent(PointTestDialog);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const type = async (field: HTMLInputElement | HTMLTextAreaElement, text: string) => {
      field.value = text;
      field.dispatchEvent(new Event('input', { bubbles: true }));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const fill = async () => {
      const picker = fixture.debugElement.children.flatMap((child) =>
        child.queryAll((node) => node.componentInstance instanceof PointPicker),
      )[0];
      (picker.componentInstance as PointPicker).pointChange.emit(POINT);
      const [first, last] = Array.from(
        root.querySelectorAll<HTMLInputElement>('input[type="date"]'),
      );
      await type(first, '2026-10-01');
      await type(last, '2026-10-15');
    };
    const save = () => root.querySelector('button[type="submit"]') as HTMLButtonElement;
    return { fixture, root, type, fill, save };
  }

  it('no deja guardar sin punto, rango y justificacion', () => {
    const { save } = render();

    expect(save().disabled).toBe(true);
  });

  it('un ultimo dia anterior al primero se rechaza y no pide conteos', async () => {
    const { fixture, root, type } = render();
    const [first, last] = Array.from(root.querySelectorAll<HTMLInputElement>('input[type="date"]'));

    await type(first, '2026-10-15');
    await type(last, '2026-10-01');
    fixture.detectChanges();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('no puede ser anterior');
    expect(actions.previewPointTest).not.toHaveBeenCalled();
  });

  it('antes de confirmar cuenta lo que se marcaria y las abiertas que quedarian sin marcar', async () => {
    const { fixture, root, fill } = render();

    await fill();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(root.textContent).toContain('Se marcarán 7 ventanas');
    });
    expect(actions.previewPointTest).toHaveBeenCalledWith(POINT, '2026-10-01', '2026-10-15');
    expect(root.textContent).toContain('2 ventanas abiertas quedarán sin marcar');
  });

  it('marca el rango con justificacion y luego muestra lo que informo el servidor', async () => {
    const { fixture, root, fill, type, save } = render();

    await fill();
    await type(root.querySelector('textarea') as HTMLTextAreaElement, 'Pruebas del proveedor');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(save().disabled).toBe(false);
    });
    save().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(actions.pointTest).toHaveBeenCalledWith(
      POINT,
      true,
      '2026-10-01',
      '2026-10-15',
      'Pruebas del proveedor',
    );
    expect(root.querySelector('[role="status"]')?.textContent).toContain(
      'Se marcaron 7 ventanas como prueba.',
    );
    expect(root.textContent).toContain('2 ventanas abiertas quedaron sin marcar');
  });

  it('si falla el conteo previo lo dice y no deja marcar sin haberlo visto', async () => {
    const { fixture, root, fill, type, save } = render();
    actions.previewPointTest.mockRejectedValue(new Error('No se pudo consultar las ventanas.'));

    await fill();
    await type(root.querySelector('textarea') as HTMLTextAreaElement, 'Pruebas');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(root.querySelector('[role="alert"]')?.textContent).toContain(
        'No se pudo consultar las ventanas.',
      );
    });

    expect(save().disabled).toBe(true);
  });

  it('quitar la prueba no necesita el conteo previo', async () => {
    const { fixture, root, fill, type, save } = render();
    actions.previewPointTest.mockRejectedValue(new Error('x'));

    await fill();
    await type(root.querySelector('textarea') as HTMLTextAreaElement, 'Eran cortes reales');
    (root.querySelectorAll('mat-radio-button input')[1] as HTMLInputElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(save().disabled).toBe(false);
    expect(save().textContent).toContain('Quitar prueba');
  });

  it('un rechazo del servidor deja el formulario abierto con el motivo', async () => {
    const { fixture, root, fill, type, save } = render();
    actions.pointTest.mockRejectedValue(new Error('El punto no admite el cambio.'));

    await fill();
    await type(root.querySelector('textarea') as HTMLTextAreaElement, 'Pruebas');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(save().disabled).toBe(false);
    });
    save().click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(root.querySelector('.form-alert')?.textContent).toContain(
      'El punto no admite el cambio.',
    );
    expect(save()).not.toBeNull();
  });
});
