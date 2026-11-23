import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';
import { vi } from 'vitest';

/** Cierra un diálogo de prueba sin abrir nada: el `MatDialogRef` y el `MatDialog` son dobles mínimos. */
export interface DialogHarness<T> {
  readonly fixture: ComponentFixture<T>;
  readonly element: HTMLElement;
  readonly close: ReturnType<typeof vi.fn>;
}

/** Crea un diálogo con sus datos y lo deja renderizado. */
export async function openDialog<T>(component: Type<T>, data: unknown): Promise<DialogHarness<T>> {
  const close = vi.fn();
  // Cada llamada arma su propio módulo: un diálogo anterior de la misma prueba deja de ser válido.
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [component],
    providers: [
      { provide: MAT_DIALOG_DATA, useValue: data },
      {
        provide: MatDialogRef,
        useValue: { close, backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
      },
      { provide: MatDialog, useValue: { open: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  await fixture.whenStable();
  // Las listas que el diálogo pide al abrirse (proveedores, responsables) se pintan en el ciclo siguiente.
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, element: fixture.nativeElement as HTMLElement, close };
}

/** Escribe en un campo del formulario como lo haría el usuario. */
export function typeInto(element: HTMLElement, label: string, value: string): void {
  const field = findField(element, label);
  const control = field.querySelector('input, textarea') as HTMLInputElement | HTMLTextAreaElement;
  control.value = value;
  control.dispatchEvent(new Event('input'));
}

/** El `mat-form-field` cuya etiqueta contiene el texto; falla si no existe. */
export function findField(element: HTMLElement, label: string): HTMLElement {
  const field = Array.from(element.querySelectorAll('mat-form-field')).find((candidate) =>
    candidate.querySelector('mat-label')?.textContent?.includes(label),
  );
  if (!field) throw new Error(`No hay un campo «${label}»`);
  return field as HTMLElement;
}

export function hasField(element: HTMLElement, label: string): boolean {
  return Array.from(element.querySelectorAll('mat-form-field')).some((candidate) =>
    candidate.querySelector('mat-label')?.textContent?.includes(label),
  );
}

/** Envía el formulario del diálogo y espera a que termine el guardado. */
export async function submitDialog<T>(harness: DialogHarness<T>): Promise<void> {
  const form = harness.element.querySelector('form') as HTMLFormElement;
  form.dispatchEvent(new Event('submit', { cancelable: true }));
  await harness.fixture.whenStable();
  harness.fixture.detectChanges();
  await harness.fixture.whenStable();
}

export function alertText(element: HTMLElement): string | undefined {
  return element.querySelector('.form-alert')?.textContent?.replace(/\s+/g, ' ').trim();
}

export function errors(element: HTMLElement): string[] {
  return Array.from(element.querySelectorAll('mat-error')).map(
    (error) => error.textContent?.replace(/\s+/g, ' ').trim() ?? '',
  );
}
