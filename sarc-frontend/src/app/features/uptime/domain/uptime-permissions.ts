import { Role } from '../../../core/auth/role';

/** Roles que capturan, corrigen y validan mediciones. El backend aplica la misma regla. */
export const UPTIME_EDITORS: readonly Role[] = ['ADMINISTRADOR', 'ANALISTA'];
