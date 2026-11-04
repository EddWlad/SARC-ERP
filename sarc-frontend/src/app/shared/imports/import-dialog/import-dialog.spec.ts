import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ImportDialog, ImportDialogData } from './import-dialog';

function render(data: ImportDialogData) {
  TestBed.configureTestingModule({
    providers: [
      { provide: MAT_DIALOG_DATA, useValue: data },
      {
        provide: MatDialogRef,
        useValue: {
          close: vi.fn(),
          disableClose: false,
          backdropClick: () => ({ subscribe: vi.fn() }),
          keydownEvents: () => ({ subscribe: vi.fn() }),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(ImportDialog);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

const base = {
  fileHint: 'Archivo',
  pasteHint: 'Filas',
  paste: () => Promise.reject(new Error('sin uso')),
};

describe('ImportDialog', () => {
  it('ofrece archivo y pegado cuando hay carga', () => {
    const element = render({ ...base, upload: () => Promise.reject(new Error('sin uso')) });

    expect(element.textContent).toContain('Pegar filas');
    expect(element.querySelector('input[type=file]')).not.toBeNull();
  });

  it('sin carga queda solo el pegado', () => {
    const element = render(base);

    expect(element.querySelector('input[type=file]')).toBeNull();
    expect(element.querySelector('mat-button-toggle-group')).toBeNull();
    expect(element.querySelector('textarea')).not.toBeNull();
  });
  it('al cargar un archivo pide su propia opción en lugar de la del pegado', () => {
    const choice = (label: string) => ({
      label,
      requiredMessage: 'Requerida',
      options: [{ value: 'x', label: 'X', hint: '' }],
    });
    const element = render({
      ...base,
      choice: choice('Ventana consultada'),
      fileChoice: choice('Semana del informe'),
      upload: () => Promise.reject(new Error('sin uso')),
    });

    expect(element.textContent).toContain('Semana del informe');
    expect(element.textContent).not.toContain('Ventana consultada');
  });
});
