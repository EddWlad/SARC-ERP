import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '../../../../core/auth/role';
import { SessionStore } from '../../../../core/auth/session-store';
import { IntermittencyPage } from './intermittency-page';

const EDITOR_SECTIONS = [
  'Tablero',
  'Registrar',
  'Importaciones',
  'Revisión',
  'Días revisados',
  'Ventanas',
];

describe('IntermittencyPage', () => {
  function sections(roles: readonly Role[]): string[] {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [IntermittencyPage],
      providers: [
        provideRouter([]),
        {
          provide: SessionStore,
          useValue: {
            hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles.includes(role)),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(IntermittencyPage);
    fixture.detectChanges();
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('nav a'),
      (link) => link.textContent?.trim() ?? '',
    );
  }

  it('el editor ve las secciones de edicion y la consulta', () => {
    expect(sections(['ANALISTA'])).toEqual(EDITOR_SECTIONS);
    expect(sections(['ADMINISTRADOR'])).toEqual(EDITOR_SECTIONS);
  });

  it('quien combina un rol de consulta con uno de edicion tambien la ve', () => {
    expect(sections(['GERENTE', 'ANALISTA'])).toEqual(EDITOR_SECTIONS);
  });

  it('un rol de consulta solo ve el tablero y el historial de ventanas', () => {
    expect(sections(['GERENTE'])).toEqual(['Tablero', 'Ventanas']);
    expect(sections(['SUPERVISOR', 'ASISTENTE'])).toEqual(['Tablero', 'Ventanas']);
  });
});
