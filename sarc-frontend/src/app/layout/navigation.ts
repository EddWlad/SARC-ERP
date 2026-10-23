import { LucideHouse, LucideIconData, LucidePlugZap, LucideUsers } from '@lucide/angular';

import { Role } from '../core/auth/role';

export interface NavItem {
  readonly label: string;
  readonly path: string;
  readonly icon: LucideIconData;
  /** Si se define, solo lo ven usuarios con alguno de estos roles. */
  readonly roles?: readonly Role[];
}

/** Menu lateral. Cada modulo nuevo (RF-002 a RF-006) agrega aqui su entrada. */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Inicio', path: '/inicio', icon: LucideHouse.icon },
  { label: 'Inventario', path: '/inventario', icon: LucidePlugZap.icon },
  { label: 'Usuarios', path: '/usuarios', icon: LucideUsers.icon, roles: ['ADMINISTRADOR'] },
];
