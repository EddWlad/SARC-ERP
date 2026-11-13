import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ShareItem } from '../../../../shared/ui/share-chart/share-chart';
import { RankedRow } from '../../../../shared/ui/top-equipment/top-equipment';
import { RankedEquipment } from '../../domain/consumption';

/** Lo que Consumo reparte por categoria: energia, que puede faltar, y las sesiones que la respaldan. */
export interface EnergyShare {
  readonly energyKwh?: number | null;
  readonly sessions: number;
}

/** Una categoria de energia para el grafico compartido: sin sesiones y sin lecturas se dicen distinto. */
export function energyShareItem(
  label: string,
  share: EnergyShare,
  quantities: QuantityFormat,
): ShareItem {
  return {
    label,
    value: share.energyKwh ?? null,
    valueText: share.sessions === 0 ? 'Sin sesiones' : quantities.knownEnergy(share.energyKwh),
  };
}

/** Sin total no hay porcentajes: ni sesiones, ni lecturas y un cero conocido se dicen distinto. */
export function energyShareEmpty(
  shares: readonly EnergyShare[],
  quantities: QuantityFormat,
): string {
  if (shares.every((share) => share.sessions === 0)) {
    return 'Sin sesiones oficiales en este periodo.';
  }
  return shares.every((share) => share.sessions === 0 || share.energyKwh == null)
    ? 'Sin lecturas de energía en este periodo.'
    : `Energía conocida: ${quantities.knownEnergy(0)} en este periodo.`;
}

/** Una fila del Top 5 de equipos por energia. */
export function rankedRow(item: RankedEquipment, quantities: QuantityFormat): RankedRow {
  return {
    position: item.position,
    name: item.equipment?.name ?? 'Sin nombre',
    code: item.equipment?.assignedCode,
    value: item.energyKwh,
    valueText: `${quantities.kwh(item.energyKwh)} kWh`,
    detail: `${quantities.count(item.sessions)} sesiones`,
  };
}
