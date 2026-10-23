import { Component, inject, input, signal } from '@angular/core';
import { email, form, FormField, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { LucideEye, LucideEyeOff, LucideTriangleAlert } from '@lucide/angular';

import { CHANGE_PASSWORD_PATH, HOME_PATH } from '../../../../core/auth/auth-guards';
import { SessionStore } from '../../../../core/auth/session-store';
import { toProblem } from '../../../../core/http/problem';
import { AuthCard } from '../auth-card/auth-card';

@Component({
  selector: 'sarc-login-page',
  imports: [
    FormField,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    LucideEye,
    LucideEyeOff,
    LucideTriangleAlert,
    AuthCard,
  ],
  templateUrl: './login-page.html',
  styleUrl: '../auth-form.scss',
})
export class LoginPage {
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  /** Destino tras ingresar, recibido como query param desde los guards. */
  readonly returnUrl = input<string>();

  protected readonly credentials = signal({ email: '', password: '' });
  protected readonly loginForm = form(this.credentials, (path) => {
    required(path.email, { message: 'Ingrese su correo' });
    email(path.email, { message: 'El correo no es válido' });
    required(path.password, { message: 'Ingrese su contraseña' });
  });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);
  protected readonly hidePassword = signal(true);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.loginForm, async () => {
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        const { email: userEmail, password } = this.credentials();
        const user = await this.session.login(userEmail, password);
        await this.router.navigateByUrl(
          user.mustChangePassword ? CHANGE_PASSWORD_PATH : this.safeReturnUrl(),
        );
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }

  /** Solo rutas internas: evita redirecciones abiertas a otros sitios. */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url !== undefined && url.startsWith('/') && !url.startsWith('//') ? url : HOME_PATH;
  }
}
