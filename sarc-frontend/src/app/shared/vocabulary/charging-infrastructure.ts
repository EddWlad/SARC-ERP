import { EquipmentRefResponse, EquipmentResponse } from '../../core/api/generated';

/**
 * Vocabulario comun de la infraestructura de carga (backend `shared/domain`): lo usan inventario,
 * consumo y los modulos siguientes, por eso no pertenece a ninguno de ellos.
 */
export type ChargingNetwork = EquipmentRefResponse['network'];
export type InfrastructureType = EquipmentRefResponse['type'];
export type EquipmentBrand = EquipmentResponse['brand'];

export const NETWORK_LABELS: Readonly<Record<ChargingNetwork, string>> = {
  KIA: 'KIA',
  JAC: 'JAC',
  CONECTZ: 'CONECTZ',
};

export const TYPE_LABELS: Readonly<Record<InfrastructureType, string>> = {
  CHARGING_POINT: 'Punto de carga',
  CHARGING_STATION: 'Electrolinera',
};

export const BRAND_LABELS: Readonly<Record<EquipmentBrand, string>> = {
  BENNY: 'Benny',
  WALLBOX: 'Wallbox',
};

export const NETWORKS = Object.keys(NETWORK_LABELS) as readonly ChargingNetwork[];
export const TYPES = Object.keys(TYPE_LABELS) as readonly InfrastructureType[];
export const BRANDS = Object.keys(BRAND_LABELS) as readonly EquipmentBrand[];
