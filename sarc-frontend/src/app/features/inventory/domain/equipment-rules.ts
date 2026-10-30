import { ChargingNetwork, InfrastructureType, UsageStatus } from './equipment';

/**
 * Reglas de identificacion por red y tipo (PRD 6.2), iguales a `EquipmentClassification` del
 * backend. Aqui solo guian el formulario; el backend es quien las hace cumplir.
 * - Punto KIA: codigo opcional solo en FREE; si existe, 6 digitos sin cero inicial; UID/PUK obligatorio.
 * - Punto JAC o CONECTZ: 1 conector, codigo igual a la serie, UID/PUK obligatorio.
 * - Electrolinera: 1 a 3 conectores de tipos distintos, codigo igual a la serie, UID/PUK opcional.
 */
export const KIA_POINT_CODE = /^[1-9]\d{5}$/;

export function isPoint(type: InfrastructureType): boolean {
  return type === 'CHARGING_POINT';
}

export function maxConnectors(type: InfrastructureType): number {
  return isPoint(type) ? 1 : 3;
}

/** El codigo asignado lo elige el usuario solo en puntos KIA; en el resto es la serie. */
export function usesSerialAsCode(network: ChargingNetwork, type: InfrastructureType): boolean {
  return !(isPoint(type) && network === 'KIA');
}

/** Un codigo propio es opcional solo para puntos KIA libres. */
export function requiresAssignedCode(
  network: ChargingNetwork,
  type: InfrastructureType,
  usage: UsageStatus | '',
): boolean {
  return !usesSerialAsCode(network, type) && usage !== 'FREE';
}
