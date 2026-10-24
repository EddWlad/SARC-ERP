import {
  CreateEquipmentRequest,
  EquipmentResponse,
  EquipmentSummaryResponse,
  PageResultEquipmentSummaryResponse,
  UpdateEquipmentRequest,
} from '../../../core/api/generated';
import {
  ChargingNetwork,
  InfrastructureType,
} from '../../../shared/vocabulary/charging-infrastructure';

// La red y el tipo son vocabulario comun; se reexportan para que el modulo los use desde su dominio.
export {
  NETWORK_LABELS,
  NETWORKS,
  TYPE_LABELS,
  TYPES,
} from '../../../shared/vocabulary/charging-infrastructure';
export type {
  ChargingNetwork,
  InfrastructureType,
} from '../../../shared/vocabulary/charging-infrastructure';

/** Contrato del backend (generado desde docs/api/openapi.json). */
export type Equipment = EquipmentResponse;
export type EquipmentSummary = EquipmentSummaryResponse;
export type EquipmentPage = PageResultEquipmentSummaryResponse;
export type CreateEquipmentInput = CreateEquipmentRequest;
export type UpdateEquipmentInput = UpdateEquipmentRequest;
export type EquipmentBrand = Equipment['brand'];
export type AdministrativeStatus = Equipment['adminStatus'];
export type UsageStatus = NonNullable<Equipment['usageStatus']>;

export const BRAND_LABELS: Readonly<Record<EquipmentBrand, string>> = {
  BENNY: 'Benny',
  WALLBOX: 'Wallbox',
};

export const ADMIN_STATUS_LABELS: Readonly<Record<AdministrativeStatus, string>> = {
  ACTIVE: 'Activo',
  INACTIVE: 'Inactivo',
  DELETED: 'Eliminado',
};

export const USAGE_STATUS_LABELS: Readonly<Record<UsageStatus, string>> = {
  FREE: 'Libre',
  BLOCKED: 'Bloqueado',
  DISCONNECTED: 'Desconectado',
};

/** Que significa cada estado de uso (PRD RF-002), para ayudas en pantalla. */
export const USAGE_STATUS_HINTS: Readonly<Record<UsageStatus, string>> = {
  FREE: 'Uso público',
  BLOCKED: 'Solo usuarios autorizados',
  DISCONNECTED: 'Instalado sin servicio',
};

export const BRANDS = Object.keys(BRAND_LABELS) as readonly EquipmentBrand[];
export const ADMIN_STATUSES = Object.keys(ADMIN_STATUS_LABELS) as readonly AdministrativeStatus[];
export const USAGE_STATUSES = Object.keys(USAGE_STATUS_LABELS) as readonly UsageStatus[];

/**
 * Filtros del listado, el resumen y los reportes (estado de pantalla); '' significa "todos".
 * Sin estado administrativo el backend excluye los eliminados.
 */
export interface EquipmentFilters {
  readonly text: string;
  readonly network: ChargingNetwork | '';
  readonly type: InfrastructureType | '';
  readonly brand: EquipmentBrand | '';
  readonly status: AdministrativeStatus | '';
  readonly usageStatus: UsageStatus | '';
}

export const NO_FILTERS: EquipmentFilters = {
  text: '',
  network: '',
  type: '',
  brand: '',
  status: '',
  usageStatus: '',
};
