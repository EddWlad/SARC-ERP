import {
  CreatedUserResponse,
  CreateUserRequest,
  PageResultUserResponse,
  UpdateUserRequest,
  UserResponse,
} from '../../../core/api/generated';
import { Role } from '../../../core/auth/role';

/** Contrato del backend (generado desde docs/api/openapi.json). */
export type User = UserResponse;
export type UserPage = PageResultUserResponse;
export type CreateUserInput = CreateUserRequest;
export type UpdateUserInput = UpdateUserRequest;
export type CreatedUser = CreatedUserResponse;
export type UserStatus = User['status'];

export const USER_STATUS_LABELS: Readonly<Record<UserStatus, string>> = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
};

export const USER_STATUSES = Object.keys(USER_STATUS_LABELS) as readonly UserStatus[];

/** Filtros del listado (estado de pantalla, no del contrato); '' significa "todos". */
export interface UserFilters {
  readonly text: string;
  readonly role: Role | '';
  readonly status: UserStatus | '';
}
