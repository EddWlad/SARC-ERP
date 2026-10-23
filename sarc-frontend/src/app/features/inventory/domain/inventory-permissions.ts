import { Role } from '../../../core/auth/role';

/**
 * PRD seccion 4: todos consultan el inventario; administradores y analistas lo editan y son los
 * unicos que ven UID/PUK y URL OCPP. Archivo aparte porque las rutas lo cargan al iniciar.
 */
export const INVENTORY_EDITORS: readonly Role[] = ['ADMINISTRADOR', 'ANALISTA'];
