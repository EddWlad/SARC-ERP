import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthApi, CurrentUser } from './auth-api';
import { Role } from './role';

/**
 * Estado de la sesion. La sesion vive en el servidor (cookie HttpOnly); aqui solo se refleja
 * quien es el usuario. `refresh()` tambien hace que el backend emita la cookie XSRF-TOKEN
 * que HttpClient reenvia en cada peticion que modifica datos.
 */
@Service()
export class SessionStore {
  private readonly api = inject(AuthApi);
  private readonly current = signal<CurrentUser | undefined>(undefined);

  readonly user = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.current() !== undefined);
  readonly mustChangePassword = computed(() => this.current()?.mustChangePassword ?? false);

  hasAnyRole(roles: readonly Role[]): boolean {
    const user = this.current();
    return user !== undefined && roles.some((role) => user.roles.includes(role));
  }

  /** Consulta la sesion vigente; si no hay, queda anonima. */
  async refresh(): Promise<void> {
    try {
      this.current.set(await firstValueFrom(this.api.me()));
    } catch {
      this.current.set(undefined);
    }
  }

  async login(email: string, password: string): Promise<CurrentUser> {
    const user = await firstValueFrom(this.api.login(email, password));
    this.current.set(user);
    return user;
  }

  /** Al salir, el backend borra la cookie CSRF; `refresh()` obtiene una nueva para el siguiente ingreso. */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } finally {
      await this.refresh();
    }
  }

  /** El backend revoca todas las sesiones del usuario: hay que volver a ingresar. */
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.api.changePassword(currentPassword, newPassword));
    this.clear();
  }

  clear(): void {
    this.current.set(undefined);
  }
}
