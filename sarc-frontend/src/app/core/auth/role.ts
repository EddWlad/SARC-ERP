import { CurrentUserResponse } from '../api/generated';

/** Roles funcionales del PRD (RF-001), tomados del contrato OpenAPI del backend. */
export type Role = CurrentUserResponse['roles'][number];

export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  ADMINISTRADOR: 'Administrador',
  GERENTE: 'Gerente de Electromovilidad',
  SUPERVISOR: 'Supervisor de Electromovilidad',
  ASISTENTE: 'Asistente de Electromovilidad',
  ANALISTA: 'Analista de Monitoreo',
};

/** Todos los roles, en el orden de presentacion. `Record<Role, ...>` garantiza que no falte ninguno. */
export const ROLES = Object.keys(ROLE_LABELS) as readonly Role[];
