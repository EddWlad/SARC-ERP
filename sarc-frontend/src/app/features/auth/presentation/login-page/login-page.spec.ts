import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { CurrentUser } from '../../../../core/auth/auth-api';
import { SessionStore } from '../../../../core/auth/session-store';
import { LoginPage } from './login-page';

const USER: CurrentUser = {
  id: 'u-1',
  email: 'ana@eos.com',
  fullName: 'Ana',
  roles: ['ANALISTA'],
  mustChangePassword: false,
};

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  const login = vi.fn<(email: string, password: string) => Promise<CurrentUser>>();
  const navigateByUrl = vi.fn(() => Promise.resolve(true));

  beforeEach(async () => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        { provide: SessionStore, useValue: { login } },
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
    fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function fillAndSubmit(email: string, password: string): Promise<void> {
    const [emailInput, passwordInput] = Array.from(element().querySelectorAll('input'));
    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
    element().querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('does not call the backend while the form is invalid and shows the errors', async () => {
    await fillAndSubmit('no-es-correo', '');

    expect(login).not.toHaveBeenCalled();
    const errors = Array.from(element().querySelectorAll('mat-error')).map((e) =>
      e.textContent?.trim(),
    );
    expect(errors).toEqual(['El correo no es válido', 'Ingrese su contraseña']);
  });

  it('logs in and opens the requested internal page', async () => {
    login.mockResolvedValue(USER);
    fixture.componentRef.setInput('returnUrl', '/usuarios');

    await fillAndSubmit('ana@eos.com', 'secreta');

    expect(login).toHaveBeenCalledWith('ana@eos.com', 'secreta');
    expect(navigateByUrl).toHaveBeenCalledWith('/usuarios');
  });

  it('ignores external return URLs', async () => {
    login.mockResolvedValue(USER);
    fixture.componentRef.setInput('returnUrl', '//evil.example');

    await fillAndSubmit('ana@eos.com', 'secreta');

    expect(navigateByUrl).toHaveBeenCalledWith('/inicio');
  });

  it('sends users with a temporary password to the change password screen', async () => {
    login.mockResolvedValue({ ...USER, mustChangePassword: true });

    await fillAndSubmit('ana@eos.com', 'secreta');

    expect(navigateByUrl).toHaveBeenCalledWith('/cambiar-clave');
  });

  it('shows the backend message when credentials are rejected', async () => {
    login.mockRejectedValue(
      new HttpErrorResponse({
        status: 401,
        error: { code: 'INVALID_CREDENTIALS', detail: 'Credenciales invalidas' },
      }),
    );

    await fillAndSubmit('ana@eos.com', 'mala');

    expect(element().querySelector('[role=alert]')?.textContent).toContain(
      'Credenciales invalidas',
    );
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
