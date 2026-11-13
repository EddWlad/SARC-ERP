import { Role } from '../../../core/auth/role';

/** Roles que capturan, importan y corrigen ventanas. El backend aplica la misma regla. */
export const INTERMITTENCY_EDITORS: readonly Role[] = ['ADMINISTRADOR', 'ANALISTA'];

/**
 * Roles que ven la disponibilidad estimada durante la etapa de prueba (FR-006). Es una lista aparte aunque
 * hoy coincida con la de edicion: cambiar quien la ve no debe cambiar quien edita.
 */
export const INTERMITTENCY_AVAILABILITY: readonly Role[] = ['ADMINISTRADOR', 'ANALISTA'];
