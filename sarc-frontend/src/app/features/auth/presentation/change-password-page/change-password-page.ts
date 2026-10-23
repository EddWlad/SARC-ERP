import { Component, inject, signal } from '@angular/core';
import { form, FormField, minLength, required, submit, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { LucideTriangleAlert } from '@lucide/angular';

import { HOME_PATH, LOGIN_PATH } from '../../../../core/auth/auth-guards';
import { SessionStore } from '../../../../core/auth/session-store';
import { toProblem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { AuthCard } from '../auth-card/auth-card';

/** Debe coincidir con PasswordPolicy.MIN_LENGTH del backend. */
const MIN_PASSWORD_LENGTH = 12;

@Component({
  selector: 'sarc-change-password-page',
  imports: [
    FormField,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    RouterLink,
    LucideTriangleAlert,
    AuthCard,
  ],
  templateUrl: './change-password-page.html',
  styleUrl: '../auth-form.scss',
})
export class ChangePasswordPage {
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);

  protected readonly mustChange = this.session.mustChangePassword;
  protected readonly homePath = HOME_PATH;
  protected readonly minLength = MIN_PASSWORD_LENGTH;

  protected readonly passwords = signal({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  protected readonly passwordForm = form(this.passwords, (path) => {
    required(path.currentPassword, { message: 'Ingrese su contraseña actual' });
    required(path.newPassword, { message: 'Ingrese la nueva contraseña' });
    minLength(path.newPassword, MIN_PASSWORD_LENGTH, {
      message: `Use al menos ${MIN_PASSWORD_LENGTH} caracteres`,
    });
    validate(path.newPassword, ({ value, valueOf }) =>
      value() !== '' && value() === valueOf(path.currentPassword)
        ? { kind: 'unchanged', message: 'Debe ser distinta de la actual' }
        : undefined,
    );
    required(path.confirmPassword, { message: 'Repita la nueva contraseña' });
    validate(path.confirmPassword, ({ value, valueOf }) =>
      value() !== valueOf(path.newPassword)
        ? { kind: 'mismatch', message: 'Las contraseñas no coinciden' }
        : undefined,
    );
  });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.passwordForm, async () => {
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        const { currentPassword, newPassword } = this.passwords();
        await this.session.changePassword(currentPassword, newPassword);
        this.notifier.success('Contraseña actualizada. Ingrese con la nueva contraseña.');
        await this.router.navigate([LOGIN_PATH]);
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }

  protected async logout(): Promise<void> {
    await this.session.logout();
    await this.router.navigate([LOGIN_PATH]);
  }
}
