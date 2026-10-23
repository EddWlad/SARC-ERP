import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import { UsersApi } from '../data-access/users-api';
import {
  CreatedUser,
  CreateUserInput,
  UpdateUserInput,
  User,
  UserFilters,
  UserPage,
} from '../domain/user';

const SEARCH_DEBOUNCE_MS = 300;
const DEFAULT_PAGE_SIZE = 20;

/**
 * Estado de la pantalla de usuarios: filtros, paginacion, listado y operaciones.
 * Vive con la pantalla (se provee en UsersPage), no es global.
 */
@Service({ autoProvided: false })
export class UsersFacade {
  private readonly api = inject(UsersApi);

  readonly filters = signal<UserFilters>({ text: '', role: '', status: '' });
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  /** El texto libre espera a que el usuario deje de escribir antes de consultar. */
  private readonly searchText = toSignal(
    toObservable(computed(() => this.filters().text)).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );

  private readonly result = httpResource<UserPage>(() =>
    this.api.listRequest({
      ...this.filters(),
      text: this.searchText(),
      page: this.pageIndex(),
      size: this.pageSize(),
    }),
  );

  readonly users = computed(() => (this.result.hasValue() ? this.result.value().content : []));
  readonly totalElements = computed(() =>
    this.result.hasValue() ? this.result.value().totalElements : 0,
  );
  readonly loading = this.result.isLoading;
  readonly failed = computed(() => this.result.error() !== undefined);

  updateFilters(changes: Partial<UserFilters>): void {
    this.filters.update((current) => ({ ...current, ...changes }));
    this.pageIndex.set(0);
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.result.reload();
  }

  async create(input: CreateUserInput): Promise<CreatedUser> {
    const created = await firstValueFrom(this.api.create(input));
    this.reload();
    return created;
  }

  async update(id: string, input: UpdateUserInput): Promise<User> {
    const updated = await firstValueFrom(this.api.update(id, input));
    this.reload();
    return updated;
  }

  async resetPassword(id: string): Promise<string> {
    const { temporaryPassword } = await firstValueFrom(this.api.resetPassword(id));
    this.reload();
    return temporaryPassword;
  }
}
