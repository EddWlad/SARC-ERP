import { EquipmentFilters } from '../domain/equipment';

/** Parametros de consulta comunes al listado, el resumen y los reportes; omite los filtros vacios. */
export function equipmentFilterParams(filters: EquipmentFilters): Record<string, string> {
  const params: Record<string, string> = {};
  const text = filters.text.trim();
  if (text !== '') {
    params['text'] = text;
  }
  for (const key of ['network', 'type', 'brand', 'status', 'usageStatus'] as const) {
    if (filters[key] !== '') {
      params[key] = filters[key];
    }
  }
  return params;
}
