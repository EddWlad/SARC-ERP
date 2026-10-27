import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth-guards';
import { INVENTORY_EDITORS } from './domain/inventory-permissions';

/** Rutas de `/inventario` (RF-002). Se cargan al entrar al modulo, no al iniciar la aplicacion. */
export const INVENTORY_ROUTES: Routes = [
  {
    path: '',
    title: 'Inventario | SARC',
    loadComponent: () =>
      import('./presentation/inventory-page/inventory-page').then((m) => m.InventoryPage),
  },
  {
    path: 'tipos-de-conector',
    title: 'Tipos de conector | SARC',
    canActivate: [roleGuard(...INVENTORY_EDITORS)],
    loadComponent: () =>
      import('./presentation/connector-types-page/connector-types-page').then(
        (m) => m.ConnectorTypesPage,
      ),
  },
  {
    path: 'importaciones',
    title: 'Carga masiva | SARC',
    canActivate: [roleGuard(...INVENTORY_EDITORS)],
    loadComponent: () =>
      import('./presentation/equipment-imports-page/equipment-imports-page').then(
        (m) => m.EquipmentImportsPage,
      ),
  },
  {
    path: 'importaciones/:id',
    title: 'Carga de equipos | SARC',
    canActivate: [roleGuard(...INVENTORY_EDITORS)],
    loadComponent: () =>
      import('./presentation/equipment-import-batch-page/equipment-import-batch-page').then(
        (m) => m.EquipmentImportBatchPage,
      ),
  },
  {
    path: ':id',
    title: 'Equipo | SARC',
    loadComponent: () =>
      import('./presentation/equipment-detail-page/equipment-detail-page').then(
        (m) => m.EquipmentDetailPage,
      ),
  },
];
