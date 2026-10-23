import {
  ChargingNetwork,
  CreateEquipmentInput,
  Equipment,
  EquipmentBrand,
  InfrastructureType,
  UpdateEquipmentInput,
  UsageStatus,
} from '../../domain/equipment';
import { isPoint, usesSerialAsCode } from '../../domain/equipment-rules';

/** Datos del formulario de equipo. '' en los selectores significa "sin elegir". */
export interface EquipmentFormValue {
  network: ChargingNetwork | '';
  type: InfrastructureType | '';
  brand: EquipmentBrand | '';
  name: string;
  serial: string;
  assignedCode: string;
  uidPuk: string;
  location: string;
  mapsUrl: string;
  ocppUrl: string;
  usageStatus: UsageStatus | '';
  /** Un punto de carga tiene un solo conector. */
  pointConnector: string;
  /** Una electrolinera tiene de 1 a 3 conectores de tipos distintos, en el orden elegido. */
  stationConnectors: string[];
}

/** Lo que el formulario entrega para crear, editar o registrar un reemplazo. */
export type EquipmentDraft = Omit<CreateEquipmentInput, 'replacesEquipmentId'>;

export function initialFormValue(equipment?: Equipment, replaces?: Equipment): EquipmentFormValue {
  const base = equipment ?? replaces;
  const connectorIds = equipment?.connectors.map((connector) => connector.connectorTypeId) ?? [];
  return {
    network: base?.network ?? '',
    type: base?.type ?? '',
    brand: equipment?.brand ?? '',
    name: base?.name ?? '',
    serial: equipment?.serial ?? '',
    assignedCode: equipment?.assignedCode ?? '',
    uidPuk: equipment?.uidPuk ?? '',
    location: base?.location ?? '',
    mapsUrl: base?.mapsUrl ?? '',
    ocppUrl: equipment?.ocppUrl ?? '',
    usageStatus: equipment?.usageStatus ?? '',
    pointConnector: connectorIds[0] ?? '',
    stationConnectors: connectorIds,
  };
}

/**
 * Convierte el formulario (ya validado) en la peticion: recorta textos, omite los vacios y solo
 * envia los campos que aplican a la red y el tipo del equipo.
 */
export function toEquipmentDraft(value: EquipmentFormValue): EquipmentDraft {
  if (value.network === '' || value.type === '' || value.brand === '') {
    throw new Error('Red, tipo y marca son obligatorios');
  }
  const point = isPoint(value.type);
  return {
    network: value.network,
    type: value.type,
    brand: value.brand,
    name: value.name.trim(),
    serial: value.serial.trim(),
    assignedCode: usesSerialAsCode(value.network, value.type)
      ? undefined
      : optional(value.assignedCode),
    uidPuk: optional(value.uidPuk),
    location: optional(value.location),
    mapsUrl: optional(value.mapsUrl),
    ocppUrl: optional(value.ocppUrl),
    usageStatus: point && value.usageStatus !== '' ? value.usageStatus : undefined,
    connectorTypeIds: point ? [value.pointConnector] : [...value.stationConnectors],
  };
}

/** Al editar, red y tipo no se envian: no cambian. La version la agrega la ficha. */
export function toUpdateInput(draft: EquipmentDraft): Omit<UpdateEquipmentInput, 'version'> {
  return {
    brand: draft.brand,
    name: draft.name,
    serial: draft.serial,
    assignedCode: draft.assignedCode,
    uidPuk: draft.uidPuk,
    location: draft.location,
    mapsUrl: draft.mapsUrl,
    ocppUrl: draft.ocppUrl,
    usageStatus: draft.usageStatus,
    connectorTypeIds: draft.connectorTypeIds,
  };
}

function optional(text: string): string | undefined {
  const trimmed = text.trim();
  return trimmed === '' ? undefined : trimmed;
}
