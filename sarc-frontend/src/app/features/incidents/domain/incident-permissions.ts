import { Role } from '../../../core/auth/role';

/**
 * Roles que crean tickets, les dan seguimiento, los corrigen, cierran y reabren (FR-090). El backend aplica la misma
 * regla: ocultar una accion es solo experiencia de usuario. Gerente, Supervisor y Asistente consultan y descargan.
 */
export const INCIDENT_EDITORS: readonly Role[] = ['ADMINISTRADOR', 'ANALISTA'];

/**
 * Rol que configura el numero inicial de tickets (FR-013). Es una lista aparte porque configurar la numeracion es
 * mas restringido que editar tickets.
 */
export const INCIDENT_NUMBERING: readonly Role[] = ['ADMINISTRADOR'];
