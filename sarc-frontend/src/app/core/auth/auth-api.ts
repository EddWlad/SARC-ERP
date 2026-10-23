import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';

import { ChangePasswordRequest, CurrentUserResponse, LoginRequest } from '../api/generated';
import { API_BASE } from '../http/api';

/** Usuario de la sesion actual (`GET /auth/me`). */
export type CurrentUser = CurrentUserResponse;

@Service()
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE}/auth`;

  me() {
    return this.http.get<CurrentUser>(`${this.baseUrl}/me`);
  }

  login(email: string, password: string) {
    const body: LoginRequest = { email, password };
    return this.http.post<CurrentUser>(`${this.baseUrl}/login`, body);
  }

  logout() {
    return this.http.post<void>(`${this.baseUrl}/logout`, null);
  }

  changePassword(currentPassword: string, newPassword: string) {
    const body: ChangePasswordRequest = { currentPassword, newPassword };
    return this.http.post<void>(`${this.baseUrl}/password/change`, body);
  }
}
