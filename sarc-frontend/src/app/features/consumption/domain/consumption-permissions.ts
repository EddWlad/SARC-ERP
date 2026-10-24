import { Role } from '../../../core/auth/role';

/**
 * PRD seccion 4: todos consultan consumo e informes; administradores y analistas importan,
 * concilian y corrigen. Archivo aparte porque las rutas lo cargan sin traer el resto del dominio.
 */
export const CONSUMPTION_EDITORS: readonly Role[] = ['ADMINISTRADOR', 'ANALISTA'];
